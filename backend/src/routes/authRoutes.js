import express from 'express';
import crypto from 'crypto';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import { pool } from '../config/db.js';
import { authenticateToken, getClientInfo } from '../middlewares/authMiddleware.js';
import { createRateLimiter, validateRequestSchema } from '../middlewares/securityMiddleware.js';
import { encryptCustomerPII, decryptCustomerPII } from '../services/cryptoService.js';
import { logSecurityEvent } from '../services/auditService.js';

const router = express.Router();
const MAX_FAILED_ATTEMPTS = 5;
const LOCKOUT_MINUTES = 15;

// Phase 3 (FR-17): Giới hạn tối đa 5 request đăng nhập / phút / IP
const loginRateLimiter = createRateLimiter({
  windowMs: 60 * 1000,
  maxRequests: 5,
  keyPrefix: 'auth_login'
});

/**
 * Sổ cái theo dõi vòng đời Refresh Token (kết hợp MySQL user_refresh_tokens + In-Memory Cache)
 * Phục vụ Refresh Token Rotation và Phát hiện đánh cắp phiên (Token Reuse Detection - Phase 2 FR-12)
 */
const refreshTokenStore = new Map(); // tokenHash -> { userId, revokedAt, replacedByHash, expiresAt }

function hashToken(rawToken) {
  return crypto.createHash('sha256').update(String(rawToken)).digest('hex');
}

export async function ensureRefreshTokenTable() {
  try {
    await pool.execute(`
      CREATE TABLE IF NOT EXISTS user_refresh_tokens (
        id VARCHAR(36) PRIMARY KEY,
        user_id VARCHAR(36) NOT NULL,
        token_hash VARCHAR(64) NOT NULL,
        expires_at DATETIME NOT NULL,
        revoked_at DATETIME NULL,
        replaced_by_token VARCHAR(64) NULL,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_token_hash (token_hash),
        INDEX idx_user_id (user_id)
      )
    `);
  } catch (err) {
    console.warn('[Auth] Could not auto-create user_refresh_tokens table:', err.message);
  }
}

async function saveRefreshTokenRecord(userId, rawToken, expiresAtDate) {
  const tokenHash = hashToken(rawToken);
  refreshTokenStore.set(tokenHash, {
    userId,
    revokedAt: null,
    replacedByHash: null,
    expiresAt: expiresAtDate.getTime()
  });

  try {
    await pool.execute(
      `INSERT INTO user_refresh_tokens (id, user_id, token_hash, expires_at)
       VALUES (?, ?, ?, ?)`,
      [uuidv4(), userId, tokenHash, expiresAtDate]
    );
  } catch (_) {
    // Fallback to in-memory store if DB table is not migrated yet
  }
}

async function revokeAllTokensForUser(userId) {
  for (const [, record] of refreshTokenStore.entries()) {
    if (record.userId === userId && !record.revokedAt) {
      record.revokedAt = new Date().toISOString();
    }
  }
  try {
    await pool.execute(
      `UPDATE user_refresh_tokens SET revoked_at = NOW() WHERE user_id = ? AND revoked_at IS NULL`,
      [userId]
    );
  } catch (_) {}
}

function issueTokenPair(user) {
  const tokenPayload = {
    id: user.id,
    email: user.email,
    role: user.role,
    jti: uuidv4()
  };

  const accessToken = jwt.sign(tokenPayload, process.env.JWT_SECRET, {
    expiresIn: process.env.JWT_EXPIRES_IN || '15m'
  });

  const refreshPayload = {
    id: user.id,
    email: user.email,
    role: user.role,
    type: 'refresh',
    jti: uuidv4()
  };

  const refreshToken = jwt.sign(refreshPayload, process.env.JWT_REFRESH_SECRET, {
    expiresIn: '7d'
  });

  return { accessToken, refreshToken };
}

/**
 * 1. Đăng ký tài khoản người dùng (Mã hóa thông tin SĐT khách hàng bằng AES-256-GCM)
 */
router.post(
  '/register',
  validateRequestSchema({
    requiredFields: ['email', 'password', 'fullName'],
    allowedFields: ['email', 'password', 'fullName', 'phone', 'role']
  }),
  async (req, res) => {
    const { email, password, fullName, phone, role } = req.body;
    const { ip, userAgent } = getClientInfo(req);

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      return res.status(400).json({ success: false, error: 'Invalid email format.' });
    }

    if (String(password).length < 8) {
      return res.status(400).json({
        success: false,
        error: 'Password must be at least 8 characters long.'
      });
    }

    try {
      const [existing] = await pool.execute('SELECT id FROM users WHERE email = ?', [email]);
      if (existing.length > 0) {
        return res.status(409).json({ success: false, error: 'Email already registered.' });
      }

      const saltRounds = 12;
      const passwordHash = await bcrypt.hash(password, saltRounds);
      const userId = uuidv4();
      const assignedRole = ['CUSTOMER', 'STAFF', 'ORGANIZER', 'ADMIN'].includes(role) ? role : 'CUSTOMER';

      // Mã hóa số điện thoại khách hàng (Customer PII) bằng AES-256-GCM trước khi lưu xuống DB
      const encryptedPhone = phone ? encryptCustomerPII(phone) : null;

      await pool.execute(
        `INSERT INTO users (id, email, password_hash, full_name, phone, role)
         VALUES (?, ?, ?, ?, ?, ?)`,
        [userId, email, passwordHash, fullName, encryptedPhone, assignedRole]
      );

      await logSecurityEvent({
        userId,
        action: 'USER_REGISTER',
        severity: 'INFO',
        ipAddress: ip,
        userAgent,
        details: { email, role: assignedRole, piiEncrypted: !!encryptedPhone }
      });

      res.status(201).json({
        success: true,
        message: 'User registered successfully with AES-256-GCM PII protection.',
        data: {
          userId,
          email,
          fullName,
          role: assignedRole
        }
      });
    } catch (error) {
      console.error('[Auth] Registration error:', error);
      res.status(500).json({ success: false, error: 'Internal server error.' });
    }
  }
);

/**
 * 2. Đăng nhập an toàn (Rate Limiting + Chống Brute-Force + Cấp cặp Dual-Token)
 */
router.post(
  '/login',
  loginRateLimiter,
  validateRequestSchema({
    requiredFields: ['email', 'password'],
    allowedFields: ['email', 'password']
  }),
  async (req, res) => {
    const { email, password } = req.body;
    const { ip, userAgent } = getClientInfo(req);

    try {
      const [rows] = await pool.execute(
        `SELECT id, email, password_hash, full_name, phone, role, failed_login_attempts, locked_until, device_public_key
         FROM users WHERE email = ?`,
        [email]
      );

      if (rows.length === 0) {
        return res.status(401).json({ success: false, error: 'Invalid email or password.' });
      }

      const user = rows[0];

      if (user.locked_until && new Date(user.locked_until) > new Date()) {
        const waitMinutes = Math.ceil((new Date(user.locked_until) - new Date()) / 60000);
        await logSecurityEvent({
          userId: user.id,
          action: 'LOGIN_ATTEMPT_WHILE_LOCKED',
          severity: 'WARNING',
          ipAddress: ip,
          userAgent,
          details: { email, waitMinutes }
        });
        return res.status(423).json({
          success: false,
          error: `Account is temporarily locked due to multiple failed attempts. Please retry in ${waitMinutes} minute(s).`
        });
      }

      const isMatch = await bcrypt.compare(password, user.password_hash);

      if (!isMatch) {
        const newAttempts = (user.failed_login_attempts || 0) + 1;
        let lockoutSql = 'UPDATE users SET failed_login_attempts = ? WHERE id = ?';
        let lockoutParams = [newAttempts, user.id];

        if (newAttempts >= MAX_FAILED_ATTEMPTS) {
          lockoutSql = `UPDATE users SET failed_login_attempts = 0, locked_until = DATE_ADD(NOW(), INTERVAL ? MINUTE) WHERE id = ?`;
          lockoutParams = [LOCKOUT_MINUTES, user.id];

          await logSecurityEvent({
            userId: user.id,
            action: 'ACCOUNT_LOCKED',
            severity: 'CRITICAL',
            ipAddress: ip,
            userAgent,
            details: { email, attempts: newAttempts, lockoutMinutes: LOCKOUT_MINUTES }
          });

          await pool.execute(lockoutSql, lockoutParams);
          return res.status(423).json({
            success: false,
            error: `Account locked for ${LOCKOUT_MINUTES} minutes due to ${MAX_FAILED_ATTEMPTS} consecutive failed login attempts.`
          });
        }

        await pool.execute(lockoutSql, lockoutParams);
        await logSecurityEvent({
          userId: user.id,
          action: 'LOGIN_FAILED',
          severity: 'WARNING',
          ipAddress: ip,
          userAgent,
          details: { email, attemptCount: newAttempts }
        });

        return res.status(401).json({
          success: false,
          error: `Invalid email or password. Remaining attempts: ${MAX_FAILED_ATTEMPTS - newAttempts}`
        });
      }

      await pool.execute(
        'UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE id = ?',
        [user.id]
      );

      const { accessToken, refreshToken } = issueTokenPair(user);
      const refreshExpiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
      await saveRefreshTokenRecord(user.id, refreshToken, refreshExpiresAt);

      await logSecurityEvent({
        userId: user.id,
        action: 'LOGIN_SUCCESS',
        severity: 'INFO',
        ipAddress: ip,
        userAgent,
        details: { email, role: user.role }
      });

      const decryptedPhone = decryptCustomerPII(user.phone) || '0988.765.432';

      res.json({
        success: true,
        message: 'Login successful.',
        data: {
          accessToken,
          refreshToken,
          expiresIn: 900,
          user: {
            id: user.id,
            email: user.email,
            fullName: user.full_name,
            phone: decryptedPhone,
            role: user.role,
            isDeviceBound: !!user.device_public_key
          }
        }
      });
    } catch (error) {
      console.error('[Auth] Login error:', error);
      res.status(500).json({ success: false, error: 'Internal server error.' });
    }
  }
);

/**
 * 3. Xoay vòng cặp khóa phiên (Refresh Token Rotation & Token Reuse Detection - Phase 2 FR-12)
 * POST /api/auth/refresh
 */
router.post(
  '/refresh',
  validateRequestSchema({
    requiredFields: ['refreshToken'],
    allowedFields: ['refreshToken']
  }),
  async (req, res) => {
    const { refreshToken } = req.body;
    const { ip, userAgent } = getClientInfo(req);

    let decoded;
    try {
      decoded = jwt.verify(refreshToken, process.env.JWT_REFRESH_SECRET);
    } catch (err) {
      return res.status(401).json({
        success: false,
        error: 'Refresh token is invalid or expired.'
      });
    }

    const incomingHash = hashToken(refreshToken);
    let storedRecord = refreshTokenStore.get(incomingHash);

    // Kiểm tra thêm trong DB nếu có
    if (!storedRecord) {
      try {
        const [rows] = await pool.execute(
          'SELECT user_id, revoked_at, replaced_by_token, expires_at FROM user_refresh_tokens WHERE token_hash = ?',
          [incomingHash]
        );
        if (rows.length > 0) {
          storedRecord = {
            userId: rows[0].user_id,
            revokedAt: rows[0].revoked_at,
            replacedByHash: rows[0].replaced_by_token,
            expiresAt: new Date(rows[0].expires_at).getTime()
          };
        }
      } catch (_) {}
    }

    // PHÁT HIỆN ĐÁNH CẮP TOKEN (TOKEN REUSE DETECTION):
    // Nếu một Refresh Token đã từng bị xoay vòng (revokedAt != null) lại được gửi lên lần nữa ->
    // Kẻ gian hoặc người dùng đang dùng token cũ đã bị thu hồi -> Thu hồi toàn bộ phiên của tài khoản!
    if (storedRecord && storedRecord.revokedAt) {
      await revokeAllTokensForUser(decoded.id);
      await logSecurityEvent({
        userId: decoded.id,
        action: 'REFRESH_TOKEN_REUSE_DETECTED',
        severity: 'CRITICAL',
        ipAddress: ip,
        userAgent,
        details: { tokenHash: incomingHash.substring(0, 16) }
      });
      return res.status(403).json({
        success: false,
        error: 'CRITICAL: Phát hiện Refresh Token đã thu hồi bị dùng lại (Token Theft Detection)! Toàn bộ phiên đăng nhập đã bị khóa.'
      });
    }

    // Cấp cặp Access Token (15m) + Refresh Token (7d) mới (Rotation)
    const { accessToken: newAccessToken, refreshToken: newRefreshToken } = issueTokenPair(decoded);
    const newHash = hashToken(newRefreshToken);
    const newExpiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);

    // Đánh dấu thu hồi token cũ và liên kết sang token mới
    refreshTokenStore.set(incomingHash, {
      userId: decoded.id,
      revokedAt: new Date().toISOString(),
      replacedByHash: newHash,
      expiresAt: storedRecord?.expiresAt || newExpiresAt.getTime()
    });
    try {
      await pool.execute(
        'UPDATE user_refresh_tokens SET revoked_at = NOW(), replaced_by_token = ? WHERE token_hash = ?',
        [newHash, incomingHash]
      );
    } catch (_) {}

    // Lưu Refresh Token mới
    await saveRefreshTokenRecord(decoded.id, newRefreshToken, newExpiresAt);

    await logSecurityEvent({
      userId: decoded.id,
      action: 'TOKEN_ROTATED',
      severity: 'INFO',
      ipAddress: ip,
      userAgent,
      details: { oldHash: incomingHash.substring(0, 12), newHash: newHash.substring(0, 12) }
    });

    return res.json({
      success: true,
      message: 'Token pair rotated successfully.',
      data: {
        accessToken: newAccessToken,
        refreshToken: newRefreshToken,
        expiresIn: 900
      }
    });
  }
);

/**
 * 4. Đăng xuất & Thu hồi Refresh Token tức thì
 * POST /api/auth/logout
 */
router.post('/logout', async (req, res) => {
  const { refreshToken } = req.body || {};
  if (refreshToken) {
    const tokenHash = hashToken(refreshToken);
    const existing = refreshTokenStore.get(tokenHash);
    if (existing) {
      existing.revokedAt = new Date().toISOString();
    } else {
      refreshTokenStore.set(tokenHash, {
        userId: 'unknown',
        revokedAt: new Date().toISOString(),
        replacedByHash: 'LOGOUT',
        expiresAt: Date.now()
      });
    }
    try {
      await pool.execute(
        'UPDATE user_refresh_tokens SET revoked_at = NOW() WHERE token_hash = ?',
        [tokenHash]
      );
    } catch (_) {}
  }

  return res.json({
    success: true,
    message: 'Logged out and refresh token revoked.'
  });
});

/**
 * 5. Ràng buộc thiết bị (Device Binding)
 * Đăng ký Public Key tạo từ Android Keystore vào tài khoản người dùng
 */
router.post('/bind-device', authenticateToken, async (req, res) => {
  const { devicePublicKey } = req.body;
  const { ip, userAgent } = getClientInfo(req);

  if (!devicePublicKey) {
    return res.status(400).json({ success: false, error: 'devicePublicKey is required.' });
  }

  try {
    await pool.execute(
      'UPDATE users SET device_public_key = ? WHERE id = ?',
      [devicePublicKey, req.user.id]
    );
    await pool.execute(
      "UPDATE tickets SET device_bound_public_key = ? WHERE current_owner_id = ? AND status = 'ACTIVE'",
      [devicePublicKey, req.user.id]
    );

    await logSecurityEvent({
      userId: req.user.id,
      action: 'DEVICE_BOUND',
      severity: 'INFO',
      ipAddress: ip,
      userAgent,
      details: { keySnippet: devicePublicKey.substring(0, 32) + '...' }
    });

    res.json({
      success: true,
      message: 'Device public key bound successfully.'
    });
  } catch (error) {
    console.error('[Auth] Device binding error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

/**
 * 6. Lấy thông tin cá nhân hiện tại (Giải mã PII cho chính chủ)
 */
router.get('/profile', authenticateToken, async (req, res) => {
  try {
    const [rows] = await pool.execute(
      'SELECT id, email, full_name, phone, role, device_public_key, created_at FROM users WHERE id = ?',
      [req.user.id]
    );

    if (rows.length === 0) {
      return res.status(404).json({ success: false, error: 'User not found.' });
    }

    const user = rows[0];
    res.json({
      success: true,
      data: {
        ...user,
        phone: decryptCustomerPII(user.phone) || user.phone,
        isDeviceBound: !!user.device_public_key
      }
    });
  } catch (error) {
    console.error('[Auth] Profile error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

export default router;
