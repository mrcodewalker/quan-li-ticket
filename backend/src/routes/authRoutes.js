import express from 'express';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import { pool } from '../config/db.js';
import { authenticateToken, getClientInfo } from '../middlewares/authMiddleware.js';
import { logSecurityEvent } from '../services/auditService.js';

const router = express.Router();
const MAX_FAILED_ATTEMPTS = 5;
const LOCKOUT_MINUTES = 15;

/**
 * Đăng ký tài khoản người dùng
 */
router.post('/register', async (req, res) => {
  const { email, password, fullName, phone, role } = req.body;
  const { ip, userAgent } = getClientInfo(req);

  if (!email || !password || !fullName) {
    return res.status(400).json({
      success: false,
      error: 'Email, password, and full name are required.'
    });
  }

  // Validate email format
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(email)) {
    return res.status(400).json({ success: false, error: 'Invalid email format.' });
  }

  // Password policy check: minimum 8 characters
  if (password.length < 8) {
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

    await pool.execute(
      `INSERT INTO users (id, email, password_hash, full_name, phone, role)
       VALUES (?, ?, ?, ?, ?, ?)`,
      [userId, email, passwordHash, fullName, phone || null, assignedRole]
    );

    await logSecurityEvent({
      userId,
      action: 'USER_REGISTER',
      severity: 'INFO',
      ipAddress: ip,
      userAgent,
      details: { email, role: assignedRole }
    });

    res.status(201).json({
      success: true,
      message: 'User registered successfully.',
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
});

/**
 * Đăng nhập an toàn (Chống Brute-Force & Rate Limiting)
 */
router.post('/login', async (req, res) => {
  const { email, password } = req.body;
  const { ip, userAgent } = getClientInfo(req);

  if (!email || !password) {
    return res.status(400).json({ success: false, error: 'Email and password are required.' });
  }

  try {
    const [rows] = await pool.execute(
      `SELECT id, email, password_hash, full_name, role, failed_login_attempts, locked_until, device_public_key
       FROM users WHERE email = ?`,
      [email]
    );

    if (rows.length === 0) {
      // Fake delay or generic response to prevent user enumeration
      return res.status(401).json({ success: false, error: 'Invalid email or password.' });
    }

    const user = rows[0];

    // Kiểm tra tài khoản có đang bị tạm khóa do nhập sai nhiều lần
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

    // Đăng nhập thành công -> Reset số lần đăng nhập sai
    await pool.execute(
      'UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE id = ?',
      [user.id]
    );

    const tokenPayload = {
      id: user.id,
      email: user.email,
      role: user.role
    };

    const accessToken = jwt.sign(tokenPayload, process.env.JWT_SECRET || 'SecureTicket2026_TopSecretJwtKey!#998244353', {
      expiresIn: process.env.JWT_EXPIRES_IN || '15m'
    });

    const refreshToken = jwt.sign(tokenPayload, process.env.JWT_REFRESH_SECRET || 'SecureTicketRefresh_SecretKey_2026!#Argon', {
      expiresIn: '7d'
    });

    await logSecurityEvent({
      userId: user.id,
      action: 'LOGIN_SUCCESS',
      severity: 'INFO',
      ipAddress: ip,
      userAgent,
      details: { email, role: user.role }
    });

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
          role: user.role,
          isDeviceBound: !!user.device_public_key
        }
      }
    });
  } catch (error) {
    console.error('[Auth] Login error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

/**
 * Ràng buộc thiết bị (Device Binding)
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
 * Lấy thông tin cá nhân hiện tại
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
        isDeviceBound: !!user.device_public_key
      }
    });
  } catch (error) {
    console.error('[Auth] Profile error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

export default router;

