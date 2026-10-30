import express from 'express';
import { v4 as uuidv4 } from 'uuid';
import { pool } from '../config/db.js';
import { authenticateToken, authorizeRoles, getClientInfo } from '../middlewares/authMiddleware.js';
import {
  verifyDynamicQrPayload,
  verifyServerSignature,
  verifyDeviceSignature,
  createTicketCanonicalString,
  generateSecureNonce
} from '../services/cryptoService.js';
import { logSecurityEvent } from '../services/auditService.js';

const router = express.Router();

// Bộ nhớ tạm lưu trữ challenge cho NFC Handshake với thời gian sống 60 giây
const nfcChallengeStore = new Map();

function cleanExpiredChallenges() {
  const now = Date.now();
  for (const [key, data] of nfcChallengeStore.entries()) {
    if (now - data.createdAt > 60000) {
      nfcChallengeStore.delete(key);
    }
  }
}
setInterval(cleanExpiredChallenges, 30000);

/**
 * Soát vé bằng mã QR động (Staff / Inspector)
 */
router.post('/validate-qr', authenticateToken, authorizeRoles('STAFF', 'ADMIN', 'ORGANIZER'), async (req, res) => {
  const { qrString } = req.body;
  const { ip, userAgent } = getClientInfo(req);

  if (!qrString) {
    return res.status(400).json({ success: false, error: 'qrString is required.' });
  }

  let parsedQr;
  try {
    parsedQr = typeof qrString === 'string' ? JSON.parse(qrString) : qrString;
  } catch (e) {
    return res.status(400).json({ success: false, error: 'Invalid QR string format.' });
  }

  const { tId, ts, totp } = parsedQr;

  if (!tId || ts === undefined || !totp) {
    return res.status(400).json({ success: false, error: 'Missing required QR fields (tId, ts, totp).' });
  }

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    const [rows] = await conn.execute(
      `SELECT t.*, e.title as event_title, u.full_name as owner_name, u.email as owner_email
       FROM tickets t
       JOIN events e ON t.event_id = e.id
       JOIN users u ON t.current_owner_id = u.id
       WHERE t.id = ? FOR UPDATE`,
      [tId]
    );

    const logId = uuidv4();

    if (rows.length === 0) {
      await conn.rollback();
      return res.status(404).json({ success: false, error: 'Ticket not found.' });
    }

    const ticket = rows[0];

    // 1. Kiểm tra trạng thái vé: Chống Double-Use / Replay Attack
    if (ticket.status === 'USED') {
      await conn.execute(
        `INSERT INTO validation_logs (id, ticket_id, staff_id, method, result, notes)
         VALUES (?, ?, ?, 'QR_ROTATING', 'FAILED_ALREADY_USED', 'Ticket was already scanned')`,
        [logId, ticket.id, req.user.id]
      );
      await conn.commit();

      await logSecurityEvent({
        userId: req.user.id,
        action: 'TICKET_REPLAY_ATTEMPT',
        severity: 'CRITICAL',
        ipAddress: ip,
        userAgent,
        details: { ticketId: ticket.id, usedAt: ticket.used_at }
      });

      return res.status(409).json({
        success: false,
        result: 'FAILED_ALREADY_USED',
        error: `Ticket has already been used on ${ticket.used_at}.`
      });
    }

    if (ticket.status !== 'ACTIVE') {
      await conn.execute(
        `INSERT INTO validation_logs (id, ticket_id, staff_id, method, result, notes)
         VALUES (?, ?, ?, 'QR_ROTATING', 'FAILED_REVOKED', ?)`,
        [logId, ticket.id, req.user.id, `Ticket status is ${ticket.status}`]
      );
      await conn.commit();

      return res.status(400).json({
        success: false,
        result: 'FAILED_REVOKED',
        error: `Ticket is not valid. Status: ${ticket.status}`
      });
    }

    // 2. Kiểm tra Dynamic TOTP QR Code (xoay vòng mỗi 30s)
    const qrCheck = verifyDynamicQrPayload(ticket.id, parseInt(ts), totp, ticket.qr_secret_seed);
    if (!qrCheck.valid) {
      await conn.execute(
        `INSERT INTO validation_logs (id, ticket_id, staff_id, method, result, notes)
         VALUES (?, ?, ?, 'QR_ROTATING', 'FAILED_INVALID_SIG', ?)`,
        [logId, ticket.id, req.user.id, qrCheck.reason]
      );
      await conn.commit();

      return res.status(400).json({
        success: false,
        result: 'FAILED_INVALID_SIG',
        error: `Dynamic QR verification failed: ${qrCheck.reason}`
      });
    }

    // 3. Xác minh Chữ ký số Server (ECDSA P-256)
    const canonical = createTicketCanonicalString(
      ticket.id,
      ticket.event_id,
      ticket.current_owner_id,
      ticket.nonce,
      ticket.issued_timestamp || Math.floor(new Date(ticket.issued_at).getTime() / 1000)
    );
    const isSigValid = verifyServerSignature(canonical, ticket.server_signature);

    if (!isSigValid) {
      await conn.execute(
        `INSERT INTO validation_logs (id, ticket_id, staff_id, method, result, notes)
         VALUES (?, ?, ?, 'QR_ROTATING', 'FAILED_INVALID_SIG', 'Forged server signature')`,
        [logId, ticket.id, req.user.id]
      );
      await conn.commit();

      await logSecurityEvent({
        userId: req.user.id,
        action: 'FORGED_TICKET_DETECTED',
        severity: 'CRITICAL',
        ipAddress: ip,
        userAgent,
        details: { ticketId: ticket.id }
      });

      return res.status(400).json({
        success: false,
        result: 'FAILED_INVALID_SIG',
        error: 'CRITICAL: Ticket digital signature is forged or invalid!'
      });
    }

    // 4. Hợp lệ -> Đánh dấu vé ĐÃ SỬ DỤNG
    const usedAt = new Date();
    await conn.execute(
      `UPDATE tickets SET status = 'USED', used_at = ?, used_by_device = ? WHERE id = ?`,
      [usedAt, req.user.id, ticket.id]
    );

    // Ghi nhận nhật ký soát vé thành công
    await conn.execute(
      `INSERT INTO validation_logs (id, ticket_id, staff_id, method, result, scanned_at)
       VALUES (?, ?, ?, 'QR_ROTATING', 'SUCCESS', ?)`,
      [logId, ticket.id, req.user.id, usedAt]
    );

    await conn.commit();

    await logSecurityEvent({
      userId: req.user.id,
      action: 'TICKET_VALIDATED_SUCCESS',
      severity: 'INFO',
      ipAddress: ip,
      userAgent,
      details: {
        ticketId: ticket.id,
        eventId: ticket.event_id,
        ownerId: ticket.current_owner_id
      }
    });

    res.json({
      success: true,
      result: 'SUCCESS',
      message: 'Ticket successfully verified and checked in!',
      data: {
        ticketCode: ticket.ticket_code,
        eventTitle: ticket.event_title,
        ownerName: ticket.owner_name,
        checkInTime: usedAt.toISOString()
      }
    });
  } catch (error) {
    await conn.rollback();
    console.error('[Validation] QR check error:', error);
    res.status(500).json({ success: false, error: 'Internal server error during validation.' });
  } finally {
    conn.release();
  }
});

/**
 * Phát sinh Challenge ngẫu nhiên cho cơ chế xác thực NFC HCE
 */
router.post('/nfc-challenge', authenticateToken, authorizeRoles('STAFF', 'ADMIN'), (req, res) => {
  const challenge = generateSecureNonce(32);
  const challengeId = uuidv4();

  nfcChallengeStore.set(challengeId, {
    challenge,
    createdAt: Date.now(),
    staffId: req.user.id
  });

  res.json({
    success: true,
    data: {
      challengeId,
      challenge,
      expiresInSeconds: 60
    }
  });
});

/**
 * Xác minh vé qua NFC HCE (Challenge - Response sử dụng Android Keystore)
 */
router.post('/validate-nfc', authenticateToken, authorizeRoles('STAFF', 'ADMIN'), async (req, res) => {
  const { ticketId, challengeId, deviceSignature } = req.body;
  const { ip, userAgent } = getClientInfo(req);

  if (!ticketId || !challengeId || !deviceSignature) {
    return res.status(400).json({
      success: false,
      error: 'ticketId, challengeId, and deviceSignature are required.'
    });
  }

  // Kiểm tra Challenge
  const challengeData = nfcChallengeStore.get(challengeId);
  if (!challengeData) {
    return res.status(400).json({
      success: false,
      result: 'FAILED_EXPIRED',
      error: 'NFC Challenge expired or invalid.'
    });
  }
  // Tiêu hủy challenge ngay sau khi đọc (Chống Replay Attack)
  nfcChallengeStore.delete(challengeId);

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    const [rows] = await conn.execute(
      `SELECT t.*, e.title as event_title, u.full_name as owner_name, u.device_public_key
       FROM tickets t
       JOIN events e ON t.event_id = e.id
       JOIN users u ON t.current_owner_id = u.id
       WHERE t.id = ? FOR UPDATE`,
      [ticketId]
    );

    const logId = uuidv4();

    if (rows.length === 0) {
      await conn.rollback();
      return res.status(404).json({ success: false, error: 'Ticket not found.' });
    }

    const ticket = rows[0];

    if (ticket.status !== 'ACTIVE') {
      await conn.execute(
        `INSERT INTO validation_logs (id, ticket_id, staff_id, method, challenge, result, notes)
         VALUES (?, ?, ?, 'NFC_HCE', ?, 'FAILED_ALREADY_USED', ?)`,
        [logId, ticket.id, req.user.id, challengeData.challenge, `Ticket is ${ticket.status}`]
      );
      await conn.commit();
      return res.status(400).json({ success: false, error: `Ticket status is ${ticket.status}` });
    }

    // Kiểm tra Device Binding: Vé phải gắn với Public Key của thiết bị
    const clientPubKey = ticket.device_bound_public_key || ticket.device_public_key;
    if (!clientPubKey) {
      await conn.execute(
        `INSERT INTO validation_logs (id, ticket_id, staff_id, method, challenge, result, notes)
         VALUES (?, ?, ?, 'NFC_HCE', ?, 'FAILED_DEVICE_MISMATCH', 'No device bound to this ticket')`,
        [logId, ticket.id, req.user.id, challengeData.challenge]
      );
      await conn.commit();
      return res.status(400).json({
        success: false,
        result: 'FAILED_DEVICE_MISMATCH',
        error: 'Ticket is not bound to any device keystore.'
      });
    }

    // Xác minh chữ ký số của thiết bị trên challenge
    const isDeviceVerified = verifyDeviceSignature(challengeData.challenge, deviceSignature, clientPubKey);

    if (!isDeviceVerified) {
      await conn.execute(
        `INSERT INTO validation_logs (id, ticket_id, staff_id, method, challenge, response_signature, result, notes)
         VALUES (?, ?, ?, 'NFC_HCE', ?, ?, 'FAILED_DEVICE_MISMATCH', 'Device signature mismatch')`,
        [logId, ticket.id, req.user.id, challengeData.challenge, deviceSignature]
      );
      await conn.commit();

      await logSecurityEvent({
        userId: req.user.id,
        action: 'NFC_DEVICE_MISMATCH',
        severity: 'CRITICAL',
        ipAddress: ip,
        userAgent,
        details: { ticketId: ticket.id }
      });

      return res.status(403).json({
        success: false,
        result: 'FAILED_DEVICE_MISMATCH',
        error: 'NFC Device verification failed! Ticket private key does not belong to this physical device.'
      });
    }

    // Hợp lệ -> Đánh dấu USED
    const usedAt = new Date();
    await conn.execute(
      `UPDATE tickets SET status = 'USED', used_at = ?, used_by_device = ? WHERE id = ?`,
      [usedAt, req.user.id, ticket.id]
    );

    await conn.execute(
      `INSERT INTO validation_logs (id, ticket_id, staff_id, method, challenge, response_signature, result, scanned_at)
       VALUES (?, ?, ?, 'NFC_HCE', ?, ?, 'SUCCESS', ?)`,
      [logId, ticket.id, req.user.id, challengeData.challenge, deviceSignature, usedAt]
    );

    await conn.commit();

    await logSecurityEvent({
      userId: req.user.id,
      action: 'NFC_CHECKIN_SUCCESS',
      severity: 'INFO',
      ipAddress: ip,
      userAgent,
      details: { ticketId: ticket.id }
    });

    res.json({
      success: true,
      result: 'SUCCESS',
      message: 'NFC HCE Check-in verified successfully!',
      data: {
        ticketCode: ticket.ticket_code,
        eventTitle: ticket.event_title,
        ownerName: ticket.owner_name,
        checkInTime: usedAt.toISOString()
      }
    });
  } catch (error) {
    await conn.rollback();
    console.error('[Validation] NFC check error:', error);
    res.status(500).json({ success: false, error: 'Internal server error during NFC validation.' });
  } finally {
    conn.release();
  }
});

export default router;
