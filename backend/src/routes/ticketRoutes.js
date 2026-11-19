import express from 'express';
import crypto from 'crypto';
import { v4 as uuidv4 } from 'uuid';
import { pool } from '../config/db.js';
import { authenticateToken, getClientInfo } from '../middlewares/authMiddleware.js';
import { createRateLimiter, validateRequestSchema } from '../middlewares/securityMiddleware.js';
import {
  signPayload,
  createTicketCanonicalString,
  generateDynamicQrPayload,
  generateSecureNonce,
  verifyDeviceSignature
} from '../services/cryptoService.js';
import { verifyCheckoutPass, cleanAndPromoteQueue } from '../services/queueService.js';
import { logSecurityEvent } from '../services/auditService.js';

const router = express.Router();

// Phase 3 (FR-17): Giới hạn tối đa 10 lượt mua vé / phút / User
const buyRateLimiter = createRateLimiter({
  windowMs: 60 * 1000,
  maxRequests: 10,
  keyPrefix: 'ticket_buy'
});

/**
 * Đặt mua vé có chữ ký số (ECDSA Server Signature + Hardware Device Binding - Phase 2 FR-10)
 * Sử dụng Database Transaction với row-locking FOR UPDATE để chống Race Condition & chống Bot cURL
 */
router.post(
  '/buy',
  authenticateToken,
  buyRateLimiter,
  validateRequestSchema({
    requiredFields: ['eventId'],
    allowedFields: ['eventId', 'passToken', 'clientNonce', 'nonce', 'timestamp', 'deviceSignature']
  }),
  async (req, res) => {
    const { eventId, passToken, clientNonce, nonce, timestamp, deviceSignature } = req.body;
    const { ip, userAgent } = getClientInfo(req);
    const effectiveNonce = clientNonce || nonce;

    // Phase 2 (FR-10): Chặn đứng Bot cURL/Python không có chữ ký phần cứng AndroidKeyStore
    if (!deviceSignature || !effectiveNonce || !timestamp) {
      await logSecurityEvent({
        userId: req.user.id,
        action: 'BOT_BUY_ATTEMPT_BLOCKED',
        severity: 'HIGH',
        ipAddress: ip,
        userAgent,
        details: { eventId, reason: 'Missing hardware deviceSignature or nonce/timestamp' }
      });
      return res.status(403).json({
        success: false,
        error: 'Chặn Bot tự động: Giao dịch mua vé bắt buộc phải có chữ ký số phần cứng AndroidKeyStore (deviceSignature, clientNonce, timestamp).'
      });
    }

    const nowSec = Math.floor(Date.now() / 1000);
    if (Math.abs(nowSec - Number(timestamp)) > 60) {
      return res.status(403).json({
        success: false,
        error: 'Chữ ký giao dịch mua vé đã hết hạn (> 60s). Vui lòng thử lại.'
      });
    }

    // Xác thực Pass Token của phòng chờ nếu có
    let queueIdToComplete = null;
    if (passToken) {
      const verifiedPass = verifyCheckoutPass(passToken, eventId, req.user.id);
      if (!verifiedPass) {
        return res.status(403).json({
          success: false,
          error: 'Phiên thanh toán trong phòng chờ đã hết hạn hoặc không hợp lệ. Vui lòng xếp hàng lại.'
        });
      }
      queueIdToComplete = verifiedPass.queueId;
    }

    const conn = await pool.getConnection();
    try {
      await conn.beginTransaction();

      const [eventRows] = await conn.execute(
        'SELECT id, title, available_tickets, ticket_price, status FROM events WHERE id = ? FOR UPDATE',
        [eventId]
      );

      if (eventRows.length === 0) {
        await conn.rollback();
        return res.status(404).json({ success: false, error: 'Event not found.' });
      }

      const event = eventRows[0];
      if (event.status === 'CANCELLED' || event.status === 'COMPLETED') {
        await conn.rollback();
        return res.status(400).json({ success: false, error: 'Event is not active for ticket purchase.' });
      }

      if (event.available_tickets <= 0) {
        await conn.rollback();
        return res.status(400).json({ success: false, error: 'Tickets are sold out.' });
      }

      const [userRows] = await conn.execute(
        'SELECT id, email, device_public_key FROM users WHERE id = ?',
        [req.user.id]
      );
      const user = userRows[0];

      // Nếu tài khoản đã bind khóa thiết bị, xác minh chữ ký ECDSA P-256 trên chuỗi BUY:<eventId>:<nonce>:<timestamp>
      if (user && user.device_public_key) {
        const buyCanonical = `BUY:${String(eventId).trim()}:${effectiveNonce}:${timestamp}`;
        const isDeviceSigValid = verifyDeviceSignature(buyCanonical, deviceSignature, user.device_public_key);
        if (!isDeviceSigValid) {
          await conn.rollback();
          await logSecurityEvent({
            userId: req.user.id,
            action: 'BUY_INVALID_DEVICE_SIGNATURE',
            severity: 'CRITICAL',
            ipAddress: ip,
            userAgent,
            details: { eventId }
          });
          return res.status(403).json({
            success: false,
            error: 'Chữ ký số phần cứng (AndroidKeyStore ECDSA) không khớp với thiết bị đã đăng ký!'
          });
        }
      }

      // Trừ 1 vé từ sự kiện
      await conn.execute(
        'UPDATE events SET available_tickets = available_tickets - 1 WHERE id = ?',
        [eventId]
      );

      // Khởi tạo các tham số mật mã cho vé
      const ticketId = uuidv4();
      const ticketCode = 'TKT-' + crypto.randomBytes(4).toString('hex').toUpperCase();
      const ticketNonce = generateSecureNonce(16);
      const qrSecretSeed = generateSecureNonce(32);
      const issuedTimestamp = Math.floor(Date.now() / 1000);
      const issuedAt = new Date(issuedTimestamp * 1000);

      // Chuỗi dữ liệu chuẩn hóa đại diện cho vé
      const canonicalString = createTicketCanonicalString(ticketId, eventId, req.user.id, ticketNonce, issuedTimestamp);

      // Ký số dữ liệu bằng Server Private Key (ECDSA P-256)
      const serverSignature = signPayload(canonicalString);

      await conn.execute(
        `INSERT INTO tickets (
          id, ticket_code, event_id, current_owner_id, original_owner_id, status,
          device_bound_public_key, server_signature, nonce, qr_secret_seed, issued_at, issued_timestamp
        ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?)`,
        [
          ticketId,
          ticketCode,
          eventId,
          req.user.id,
          req.user.id,
          user?.device_public_key || null,
          serverSignature,
          ticketNonce,
          qrSecretSeed,
          issuedAt,
          issuedTimestamp
        ]
      );

      if (queueIdToComplete) {
        await conn.execute("UPDATE checkout_queue SET status = 'COMPLETED' WHERE id = ?", [queueIdToComplete]);
      }

      await conn.commit();

      cleanAndPromoteQueue(eventId).catch(err => console.error('[QueuePromote] Error:', err.message));

      await logSecurityEvent({
        userId: req.user.id,
        action: 'TICKET_PURCHASE',
        severity: 'INFO',
        ipAddress: ip,
        userAgent,
        details: {
          ticketId,
          ticketCode,
          eventId,
          eventTitle: event.title,
          hasDeviceBinding: !!user?.device_public_key
        }
      });

      res.status(201).json({
        success: true,
        message: 'Ticket purchased and digitally signed with ECDSA P-256.',
        data: {
          ticketId,
          ticketCode,
          eventId,
          eventTitle: event.title,
          issuedAt: issuedAt.toISOString(),
          issuedTimestamp,
          serverSignature,
          nonce: ticketNonce,
          qrSecretSeed,
          isDeviceBound: !!user?.device_public_key
        }
      });
    } catch (error) {
      await conn.rollback();
      console.error('[Tickets] Purchase error:', error);
      res.status(500).json({ success: false, error: 'Internal server error while issuing ticket.' });
    } finally {
      conn.release();
    }
  }
);

/**
 * Lấy danh sách vé trong ví của người dùng
 */
router.get('/my-tickets', authenticateToken, async (req, res) => {
  try {
    const [tickets] = await pool.execute(
      `SELECT t.id, t.ticket_code, t.status, t.issued_at, t.issued_timestamp, t.used_at,
              t.current_owner_id, t.server_signature, t.nonce, t.qr_secret_seed, t.device_bound_public_key,
              e.id as event_id, e.title as event_title, e.location as event_location,
              e.start_time as event_start_time, e.end_time as event_end_time
       FROM tickets t
       JOIN events e ON t.event_id = e.id
       WHERE t.current_owner_id = ?
       ORDER BY t.issued_at DESC`,
      [req.user.id]
    );

    res.json({
      success: true,
      data: tickets
    });
  } catch (error) {
    console.error('[Tickets] My tickets fetch error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

/**
 * Lấy chi tiết một vé cụ thể
 */
router.get('/:id', authenticateToken, async (req, res) => {
  const { id } = req.params;
  const isStaff = ['STAFF', 'ADMIN', 'ORGANIZER'].includes(req.user.role) ? 1 : 0;
  try {
    const [rows] = await pool.execute(
      `SELECT t.*, e.title as event_title, e.location as event_location,
              e.start_time as event_start_time, e.end_time as event_end_time,
              u.full_name as owner_name, u.email as owner_email
       FROM tickets t
       JOIN events e ON t.event_id = e.id
       JOIN users u ON t.current_owner_id = u.id
       WHERE t.id = ? AND (t.current_owner_id = ? OR ? = 1)`,
      [id, req.user.id, isStaff]
    );

    if (rows.length === 0) {
      return res.status(404).json({ success: false, error: 'Ticket not found or access denied.' });
    }

    res.json({
      success: true,
      data: rows[0]
    });
  } catch (error) {
    console.error('[Tickets] Get ticket error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

/**
 * Sinh mã QR động (TOTP) cho vé (xoay vòng mỗi 30s)
 */
router.get('/:id/dynamic-qr', authenticateToken, async (req, res) => {
  const { id } = req.params;
  try {
    const [rows] = await pool.execute(
      'SELECT id, qr_secret_seed, status, current_owner_id, server_signature FROM tickets WHERE id = ?',
      [id]
    );

    if (rows.length === 0) {
      return res.status(404).json({ success: false, error: 'Ticket not found.' });
    }

    const ticket = rows[0];

    if (ticket.current_owner_id !== req.user.id && !['STAFF', 'ADMIN'].includes(req.user.role)) {
      return res.status(403).json({ success: false, error: 'Unauthorized to view QR for this ticket.' });
    }

    if (ticket.status !== 'ACTIVE') {
      return res.status(400).json({
        success: false,
        error: `Cannot generate QR for ticket with status '${ticket.status}'`
      });
    }

    const qrData = generateDynamicQrPayload(ticket.id, ticket.qr_secret_seed);

    const qrString = JSON.stringify({
      tId: ticket.id,
      ts: qrData.timeSlice,
      totp: qrData.totpToken,
      sig: ticket.server_signature
    });

    res.json({
      success: true,
      data: {
        ...qrData,
        qrString
      }
    });
  } catch (error) {
    console.error('[Tickets] Dynamic QR error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

/**
 * Chuyển nhượng vé an toàn (Atomic Transfer + Chữ ký số phần cứng AndroidKeyStore - Phase 2 FR-09)
 */
router.post(
  '/transfer',
  authenticateToken,
  validateRequestSchema({
    requiredFields: ['ticketId', 'recipientEmail'],
    allowedFields: ['ticketId', 'recipientEmail', 'clientNonce', 'nonce', 'timestamp', 'deviceSignature']
  }),
  async (req, res) => {
    const { ticketId, recipientEmail, clientNonce, nonce, timestamp, deviceSignature } = req.body;
    const { ip, userAgent } = getClientInfo(req);
    const effectiveNonce = clientNonce || nonce;

    // Phase 2 (FR-09): Bắt buộc có chữ ký phần cứng ECDSA của thiết bị chủ vé
    if (!deviceSignature || !effectiveNonce || !timestamp) {
      await logSecurityEvent({
        userId: req.user.id,
        action: 'TRANSFER_MISSING_DEVICE_SIGNATURE',
        severity: 'CRITICAL',
        ipAddress: ip,
        userAgent,
        details: { ticketId, recipientEmail }
      });
      return res.status(403).json({
        success: false,
        error: 'Từ chối chuyển vé: Bắt buộc phải xác thực vân tay và ký số bằng khóa phần cứng AndroidKeyStore (deviceSignature)!'
      });
    }

    const nowSec = Math.floor(Date.now() / 1000);
    if (Math.abs(nowSec - Number(timestamp)) > 60) {
      return res.status(403).json({
        success: false,
        error: 'Chữ ký chuyển nhượng vé đã quá hạn 60 giây (Replay blocked).'
      });
    }

    const conn = await pool.getConnection();
    try {
      await conn.beginTransaction();

      // Khóa vé để kiểm tra quyền sở hữu
      const [ticketRows] = await conn.execute(
        'SELECT id, current_owner_id, status, event_id, nonce, issued_at, device_bound_public_key FROM tickets WHERE id = ? FOR UPDATE',
        [ticketId]
      );

      if (ticketRows.length === 0) {
        await conn.rollback();
        return res.status(404).json({ success: false, error: 'Ticket not found.' });
      }

      const ticket = ticketRows[0];

      if (ticket.current_owner_id !== req.user.id) {
        await conn.rollback();
        return res.status(403).json({ success: false, error: 'You are not the owner of this ticket.' });
      }

      if (ticket.status !== 'ACTIVE') {
        await conn.rollback();
        return res.status(400).json({ success: false, error: `Ticket cannot be transferred. Current status: ${ticket.status}` });
      }

      // Lấy khóa công khai của thiết bị chủ sở hữu hiện tại
      const [ownerRows] = await conn.execute(
        'SELECT id, device_public_key FROM users WHERE id = ?',
        [req.user.id]
      );
      const ownerPubKey = ticket.device_bound_public_key || ownerRows[0]?.device_public_key;

      if (ownerPubKey) {
        const canonicalTransfer = `TRANSFER:${String(ticketId).trim()}:${String(recipientEmail).trim().toLowerCase()}:${effectiveNonce}:${timestamp}`;
        const isSigValid = verifyDeviceSignature(canonicalTransfer, deviceSignature, ownerPubKey);
        if (!isSigValid) {
          await conn.rollback();
          await logSecurityEvent({
            userId: req.user.id,
            action: 'TRANSFER_INVALID_DEVICE_SIGNATURE',
            severity: 'CRITICAL',
            ipAddress: ip,
            userAgent,
            details: { ticketId, recipientEmail }
          });
          return res.status(403).json({
            success: false,
            error: 'Chữ ký thiết bị không hợp lệ hoặc không thuộc điện thoại chính chủ!'
          });
        }
      }

      // Tìm người nhận
      const [recipientRows] = await conn.execute(
        'SELECT id, email, device_public_key FROM users WHERE email = ?',
        [recipientEmail]
      );

      if (recipientRows.length === 0) {
        await conn.rollback();
        return res.status(404).json({ success: false, error: 'Recipient user not found.' });
      }

      const recipient = recipientRows[0];

      if (recipient.id === req.user.id) {
        await conn.rollback();
        return res.status(400).json({ success: false, error: 'Cannot transfer ticket to yourself.' });
      }

      // Cập nhật chủ sở hữu mới và đổi seed mã QR để chủ cũ mất quyền truy cập mã QR hợp lệ
      const newQrSeed = generateSecureNonce(32);
      const newNonce = generateSecureNonce(16);
      const transferTimestamp = Math.floor(Date.now() / 1000);
      const transferTime = new Date(transferTimestamp * 1000);

      // Tạo chữ ký mới của Server cho chủ sở hữu mới
      const newCanonical = createTicketCanonicalString(ticket.id, ticket.event_id, recipient.id, newNonce, transferTimestamp);
      const newSignature = signPayload(newCanonical);

      await conn.execute(
        `UPDATE tickets SET
          current_owner_id = ?,
          device_bound_public_key = ?,
          server_signature = ?,
          nonce = ?,
          qr_secret_seed = ?,
          issued_timestamp = ?
         WHERE id = ?`,
        [recipient.id, recipient.device_public_key || null, newSignature, newNonce, newQrSeed, transferTimestamp, ticket.id]
      );

      const transferId = uuidv4();
      await conn.execute(
        `INSERT INTO ticket_transfers (id, ticket_id, from_user_id, to_user_id, status, transferred_at)
         VALUES (?, ?, ?, ?, 'COMPLETED', ?)`,
        [transferId, ticket.id, req.user.id, recipient.id, transferTime]
      );

      await conn.commit();

      await logSecurityEvent({
        userId: req.user.id,
        action: 'TICKET_TRANSFERRED',
        severity: 'INFO',
        ipAddress: ip,
        userAgent,
        details: {
          ticketId: ticket.id,
          fromUserId: req.user.id,
          toUserId: recipient.id,
          transferId
        }
      });

      res.json({
        success: true,
        message: 'Ticket transferred with hardware ECDSA signature verification and QR seed rotated.',
        data: {
          ticketId: ticket.id,
          transferredTo: recipient.email,
          transferredAt: transferTime.toISOString()
        }
      });
    } catch (error) {
      await conn.rollback();
      console.error('[Tickets] Transfer error:', error);
      res.status(500).json({ success: false, error: 'Internal server error during transfer.' });
    } finally {
      conn.release();
    }
  }
);

export default router;
