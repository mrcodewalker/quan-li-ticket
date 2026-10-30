import express from 'express';
import crypto from 'crypto';
import { v4 as uuidv4 } from 'uuid';
import { pool } from '../config/db.js';
import { authenticateToken, getClientInfo } from '../middlewares/authMiddleware.js';
import {
  signPayload,
  createTicketCanonicalString,
  generateDynamicQrPayload,
  generateSecureNonce,
  getServerPublicKey
} from '../services/cryptoService.js';
import { verifyCheckoutPass, cleanAndPromoteQueue } from '../services/queueService.js';
import { logSecurityEvent } from '../services/auditService.js';

const router = express.Router();

/**
 * Đặt mua vé có chữ ký số (ECDSA Server Signature + Device Binding)
 * Sử dụng Database Transaction với row-locking FOR UPDATE để chống Race Condition
 */
router.post('/buy', authenticateToken, async (req, res) => {
  const { eventId, passToken } = req.body;
  const { ip, userAgent } = getClientInfo(req);

  if (!eventId) {
    return res.status(400).json({ success: false, error: 'eventId is required.' });
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

    // Khóa dòng sự kiện để kiểm tra số lượng vé còn lại
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

    // Lấy thông tin thiết bị đã bind của user (nếu có)
    const [userRows] = await conn.execute(
      'SELECT id, email, device_public_key FROM users WHERE id = ?',
      [req.user.id]
    );
    const user = userRows[0];

    // Trừ 1 vé từ sự kiện
    await conn.execute(
      'UPDATE events SET available_tickets = available_tickets - 1 WHERE id = ?',
      [eventId]
    );

    // Khởi tạo các tham số mật mã cho vé
    const ticketId = uuidv4();
    const ticketCode = 'TKT-' + crypto.randomBytes(4).toString('hex').toUpperCase();
    const nonce = generateSecureNonce(16);
    const qrSecretSeed = generateSecureNonce(32);
    const issuedTimestamp = Math.floor(Date.now() / 1000);
    const issuedAt = new Date(issuedTimestamp * 1000);

    // Chuỗi dữ liệu chuẩn hóa đại diện cho vé
    const canonicalString = createTicketCanonicalString(ticketId, eventId, req.user.id, nonce, issuedTimestamp);

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
        user.device_public_key || null,
        serverSignature,
        nonce,
        qrSecretSeed,
        issuedAt,
        issuedTimestamp
      ]
    );

    // Đánh dấu phiên thanh toán hoàn tất để giải phóng slot cho người tiếp theo trong hàng đợi
    if (queueIdToComplete) {
      await conn.execute("UPDATE checkout_queue SET status = 'COMPLETED' WHERE id = ?", [queueIdToComplete]);
    }

    await conn.commit();

    // Đôn người tiếp theo trong hàng đợi vào trang thanh toán
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
        hasDeviceBinding: !!user.device_public_key
      }
    });

    res.status(201).json({
      success: true,
      message: 'Ticket purchased successfully.',
      data: {
        ticketId,
        ticketCode,
        eventId,
        eventTitle: event.title,
        issuedAt: issuedAt.toISOString(),
        serverSignature,
        nonce,
        qrSecretSeed, // Cung cấp cho app để sinh dynamic QR offline nếu cần
        isDeviceBound: !!user.device_public_key
      }
    });
  } catch (error) {
    await conn.rollback();
    console.error('[Tickets] Purchase error:', error);
    res.status(500).json({ success: false, error: 'Internal server error while issuing ticket.' });
  } finally {
    conn.release();
  }
});

/**
 * Lấy danh sách vé trong ví của người dùng
 */
router.get('/my-tickets', authenticateToken, async (req, res) => {
  try {
    const [tickets] = await pool.execute(
      `SELECT t.id, t.ticket_code, t.status, t.issued_at, t.used_at,
              t.server_signature, t.nonce, t.qr_secret_seed, t.device_bound_public_key,
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
  try {
    const [rows] = await pool.execute(
      `SELECT t.*, e.title as event_title, e.location as event_location,
              e.start_time as event_start_time, e.end_time as event_end_time,
              u.full_name as owner_name, u.email as owner_email
       FROM tickets t
       JOIN events e ON t.event_id = e.id
       JOIN users u ON t.current_owner_id = u.id
       WHERE t.id = ? AND (t.current_owner_id = ? OR req_user_is_staff = 1)`,
      [id, req.user.id]
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

    // Chặn IDOR: chỉ chủ vé hoặc staff mới lấy được QR
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

    // Chuỗi mã hóa tổng hợp đưa vào QR
    // Gồm: TicketId | TimeSlice | TOTP_Token | SignatureSnippet
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
 * Chuyển nhượng vé an toàn (Atomic Transfer & Chống Double Spending)
 */
router.post('/transfer', authenticateToken, async (req, res) => {
  const { ticketId, recipientEmail } = req.body;
  const { ip, userAgent } = getClientInfo(req);

  if (!ticketId || !recipientEmail) {
    return res.status(400).json({ success: false, error: 'ticketId and recipientEmail are required.' });
  }

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    // Khóa vé để kiểm tra quyền sở hữu
    const [ticketRows] = await conn.execute(
      'SELECT id, current_owner_id, status, event_id, nonce, issued_at FROM tickets WHERE id = ? FOR UPDATE',
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

    // Tạo chữ ký mới cho chủ sở hữu mới
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

    // Ghi nhận lịch sử chuyển nhượng
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
      message: 'Ticket transferred successfully.',
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
});

export default router;
