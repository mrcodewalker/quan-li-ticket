import express from 'express';
import crypto from 'crypto';
import { v4 as uuidv4 } from 'uuid';
import { pool } from '../config/db.js';
import { authenticateToken, getClientInfo } from '../middlewares/authMiddleware.js';
import {
  signPayload,
  createTicketCanonicalString,
  generateSecureNonce
} from '../services/cryptoService.js';
import { verifyCheckoutPass, cleanAndPromoteQueue } from '../services/queueService.js';
import { logSecurityEvent } from '../services/auditService.js';
import { generateVietQrEmvco } from '../services/vietqrService.js';

const router = express.Router();

const BANK_ID = process.env.BANK_ID || 'MB';
const BANK_BIN = process.env.BANK_BIN || '970422';
const BANK_ACCOUNT_NO = process.env.BANK_ACCOUNT_NO || '0696961698888';
const BANK_ACCOUNT_NAME = process.env.BANK_ACCOUNT_NAME || 'MB BANK';
const VIETQR_TEMPLATE = process.env.VIETQR_TEMPLATE || 'compact2';
const PAYMENT_WEBHOOK_SECRET = process.env.PAYMENT_WEBHOOK_SECRET || 'SecureTicket_Payment_Webhook_HMAC_Secret_2026';

/**
 * Hàm hỗ trợ xuất vé an toàn sau khi thanh toán thành công
 */
async function issueTicketOnPayment(conn, order, user) {
  const ticketId = uuidv4();
  const ticketCode = 'TKT-' + crypto.randomBytes(4).toString('hex').toUpperCase();
  const nonce = generateSecureNonce(16);
  const qrSecretSeed = generateSecureNonce(32);
  const issuedTimestamp = Math.floor(Date.now() / 1000);
  const issuedAt = new Date(issuedTimestamp * 1000);

  // Chuỗi canonical chuẩn hóa để ký số bằng Server Private Key (ECDSA P-256)
  const canonicalString = createTicketCanonicalString(ticketId, order.event_id, order.user_id, nonce, issuedTimestamp);
  const serverSignature = signPayload(canonicalString);

  await conn.execute(
    `INSERT INTO tickets (
      id, ticket_code, event_id, current_owner_id, original_owner_id, status,
      device_bound_public_key, server_signature, nonce, qr_secret_seed, issued_at, issued_timestamp
    ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?)`,
    [
      ticketId,
      ticketCode,
      order.event_id,
      order.user_id,
      order.user_id,
      user.device_public_key || null,
      serverSignature,
      nonce,
      qrSecretSeed,
      issuedAt,
      issuedTimestamp
    ]
  );

  // Cập nhật trạng thái đơn hàng sang PAID và gắn ticket_id
  await conn.execute(
    `UPDATE orders SET status = 'PAID', ticket_id = ?, paid_at = NOW() WHERE id = ?`,
    [ticketId, order.id]
  );

  // Lấy toàn bộ thông tin vé đầy đủ (kèm thông tin sự kiện) để trả về cho Android app
  const [fullTicketRows] = await conn.execute(
    `SELECT t.id, t.ticket_code, t.event_id, t.status, t.issued_at,
            t.server_signature, t.nonce, t.qr_secret_seed, t.device_bound_public_key,
            e.title as event_title, e.location as event_location, e.start_time as event_start_time
     FROM tickets t
     JOIN events e ON t.event_id = e.id
     WHERE t.id = ?`,
    [ticketId]
  );

  return fullTicketRows[0];
}

/**
 * 1. Tạo đơn hàng và mã thanh toán VietQR động
 * POST /api/payment/create-vietqr
 */
router.post('/create-vietqr', authenticateToken, async (req, res) => {
  const { eventId, passToken } = req.body;
  const { ip, userAgent } = getClientInfo(req);

  if (!eventId) {
    return res.status(400).json({ success: false, error: 'eventId is required.' });
  }

  // Xác thực quyền vào mua từ hàng đợi ảo (nếu có passToken)
  let queueId = null;
  if (passToken) {
    const verifiedPass = verifyCheckoutPass(passToken, eventId, req.user.id);
    if (!verifiedPass) {
      return res.status(403).json({
        success: false,
        error: 'Phiên phòng chờ đã hết hạn hoặc không hợp lệ. Vui lòng xếp hàng lại.'
      });
    }
    queueId = verifiedPass.queueId;
  }

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    // Khóa dòng event để kiểm tra vé và giá tiền
    const [eventRows] = await conn.execute(
      'SELECT id, title, available_tickets, ticket_price, status FROM events WHERE id = ? FOR UPDATE',
      [eventId]
    );

    if (eventRows.length === 0) {
      await conn.rollback();
      return res.status(404).json({ success: false, error: 'Event not found.' });
    }

    const event = eventRows[0];
    if (event.status !== 'UPCOMING' && event.status !== 'ONGOING') {
      await conn.rollback();
      return res.status(400).json({ success: false, error: 'Sự kiện không còn mở bán vé.' });
    }

    if (event.available_tickets <= 0) {
      await conn.rollback();
      return res.status(400).json({ success: false, error: 'Vé đã được bán hết.' });
    }

    // Tạm giữ 1 vé
    await conn.execute(
      'UPDATE events SET available_tickets = available_tickets - 1 WHERE id = ?',
      [eventId]
    );

    const orderId = uuidv4();
    // Sinh order_code ngẫu nhiên 6 chữ số
    const orderCode = String(Math.floor(100000 + Math.random() * 900000));
    const transferContent = `SECURE ${orderCode}`;
    const amount = Number(event.ticket_price);

    // Thời hạn thanh toán: 5 phút
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000);

    // Sinh mã chuẩn EMVCo Napas 247 để mọi app ngân hàng (MB, VCB, TCB,...) đều quét được
    const qrPayload = generateVietQrEmvco({
      bankBin: BANK_BIN,
      accountNo: BANK_ACCOUNT_NO,
      amount,
      memo: transferContent
    });

    // Sinh link VietQR chuẩn Napas 247
    // Format: https://img.vietqr.io/image/<BANK_ID>-<ACCOUNT_NO>-<TEMPLATE>.png?amount=<AMOUNT>&addInfo=<CONTENT>&accountName=<NAME>
    const qrImageUrl = `https://img.vietqr.io/image/${BANK_ID}-${BANK_ACCOUNT_NO}-${VIETQR_TEMPLATE}.png?amount=${amount}&addInfo=${encodeURIComponent(transferContent)}&accountName=${encodeURIComponent(BANK_ACCOUNT_NAME)}`;

    await conn.execute(
      `INSERT INTO orders (
        id, order_code, event_id, user_id, amount, status, payment_method,
        qr_image_url, transfer_content, expires_at
      ) VALUES (?, ?, ?, ?, ?, 'PENDING', 'VIETQR', ?, ?, ?)`,
      [orderId, orderCode, eventId, req.user.id, amount, qrImageUrl, transferContent, expiresAt]
    );

    await conn.commit();

    await logSecurityEvent({
      userId: req.user.id,
      action: 'PAYMENT_ORDER_CREATED',
      severity: 'INFO',
      ipAddress: ip,
      userAgent,
      details: { orderId, orderCode, eventId, amount }
    });

    res.status(201).json({
      success: true,
      message: 'Tạo mã thanh toán VietQR thành công.',
      data: {
        orderId,
        orderCode,
        eventId,
        eventTitle: event.title,
        amount,
        bankId: BANK_ID,
        bankAccountNo: BANK_ACCOUNT_NO,
        accountName: BANK_ACCOUNT_NAME,
        transferContent,
        qrImageUrl,
        qrPayload,
        expiresAt: expiresAt.toISOString(),
        ttlSeconds: 300
      }
    });
  } catch (error) {
    await conn.rollback();
    console.error('[Payment] Create order error:', error);
    res.status(500).json({ success: false, error: 'Internal server error while creating payment order.' });
  } finally {
    conn.release();
  }
});

/**
 * 2. Kiểm tra trạng thái đơn hàng
 * GET /api/payment/order/:orderId
 */
router.get('/order/:orderId', authenticateToken, async (req, res) => {
  const { orderId } = req.params;

  try {
    const [orders] = await pool.execute(
      `SELECT o.*, e.title as event_title, e.location as event_location, e.start_time as event_start_time
       FROM orders o
       JOIN events e ON o.event_id = e.id
       WHERE o.id = ?`,
      [orderId]
    );

    if (orders.length === 0) {
      return res.status(404).json({ success: false, error: 'Order not found.' });
    }

    const order = orders[0];

    // Chặn IDOR: chỉ chủ đơn hàng hoặc staff/admin
    if (order.user_id !== req.user.id && !['STAFF', 'ADMIN'].includes(req.user.role)) {
      return res.status(403).json({ success: false, error: 'Access denied.' });
    }

    // Kiểm tra nếu đơn hàng đã quá hạn mà chưa thanh toán -> hoàn trả vé
    const now = new Date();
    if (order.status === 'PENDING' && new Date(order.expires_at) < now) {
      const conn = await pool.getConnection();
      try {
        await conn.beginTransaction();
        await conn.execute("UPDATE orders SET status = 'EXPIRED' WHERE id = ? AND status = 'PENDING'", [order.id]);
        await conn.execute("UPDATE events SET available_tickets = available_tickets + 1 WHERE id = ?", [order.event_id]);
        await conn.commit();
        order.status = 'EXPIRED';
      } catch (e) {
        await conn.rollback();
      } finally {
        conn.release();
      }
    }

    // Nếu đã PAID, lấy thêm thông tin chi tiết vé đầy đủ
    let ticketData = null;
    if (order.status === 'PAID' && order.ticket_id) {
      const [ticketRows] = await pool.execute(
        `SELECT t.id, t.ticket_code, t.event_id, t.status, t.issued_at,
                t.server_signature, t.nonce, t.qr_secret_seed, t.device_bound_public_key,
                e.title as event_title, e.location as event_location, e.start_time as event_start_time
         FROM tickets t
         JOIN events e ON t.event_id = e.id
         WHERE t.id = ?`,
        [order.ticket_id]
      );
      if (ticketRows.length > 0) {
        ticketData = ticketRows[0];
      }
    }

    res.json({
      success: true,
      data: {
        orderId: order.id,
        orderCode: order.order_code,
        status: order.status,
        amount: order.amount,
        eventTitle: order.event_title,
        transferContent: order.transfer_content,
        qrImageUrl: order.qr_image_url,
        qrPayload: generateVietQrEmvco({
          bankBin: BANK_BIN,
          accountNo: BANK_ACCOUNT_NO,
          amount: order.amount,
          memo: order.transfer_content
        }),
        expiresAt: order.expires_at,
        paidAt: order.paid_at,
        ticket: ticketData
      }
    });
  } catch (error) {
    console.error('[Payment] Get order status error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

/**
 * 3. Webhook nhận thông báo biến động số dư từ Cổng thanh toán / Ngân hàng (Napas247 / PayOS / SePay)
 * POST /api/payment/webhook
 * Bảo mật: Xác thực chữ ký số HMAC-SHA256
 */
router.post('/webhook', async (req, res) => {
  const { code, desc, data, signature } = req.body;
  const { ip, userAgent } = getClientInfo(req);

  // Kiểm tra chữ ký số HMAC-SHA256
  if (!signature || !data) {
    return res.status(400).json({ success: false, error: 'Missing signature or payload data.' });
  }

  try {
    // Sắp xếp các khóa và tạo chuỗi HMAC chuẩn hóa
    const sortedKeys = Object.keys(data).sort();
    const signString = sortedKeys.map(k => `${k}=${data[k]}`).join('&');
    const expectedSignature = crypto
      .createHmac('sha256', PAYMENT_WEBHOOK_SECRET)
      .update(signString)
      .digest('hex');

    // Chống Timing Attack bằng timingSafeEqual
    const sigBuffer = Buffer.from(signature, 'hex');
    const expectedBuffer = Buffer.from(expectedSignature, 'hex');

    if (sigBuffer.length !== expectedBuffer.length || !crypto.timingSafeEqual(sigBuffer, expectedBuffer)) {
      await logSecurityEvent({
        action: 'PAYMENT_WEBHOOK_TAMPERED',
        severity: 'CRITICAL',
        ipAddress: ip,
        userAgent,
        details: { receivedSignature: signature, expectedSignature }
      });
      return res.status(403).json({ success: false, error: 'Chữ ký số Webhook không hợp lệ! Bị từ chối vì bảo mật.' });
    }

    const { orderCode, amount } = data;

    const conn = await pool.getConnection();
    try {
      await conn.beginTransaction();

      const [orderRows] = await conn.execute(
        'SELECT * FROM orders WHERE order_code = ? FOR UPDATE',
        [orderCode]
      );

      if (orderRows.length === 0) {
        await conn.rollback();
        return res.status(404).json({ success: false, error: 'Order code not found.' });
      }

      const order = orderRows[0];

      if (order.status === 'PAID') {
        await conn.rollback();
        return res.json({ success: true, message: 'Order already marked as PAID.' });
      }

      if (order.status === 'EXPIRED' || order.status === 'CANCELLED') {
        await conn.rollback();
        return res.status(400).json({ success: false, error: `Order is already ${order.status}.` });
      }

      // Kiểm tra số tiền chuyển khoản
      if (Number(amount) < Number(order.amount)) {
        await conn.rollback();
        return res.status(400).json({ success: false, error: 'Số tiền thanh toán không đủ.' });
      }

      // Lấy thông tin người dùng
      const [userRows] = await conn.execute(
        'SELECT id, email, device_public_key FROM users WHERE id = ?',
        [order.user_id]
      );
      const user = userRows[0];

      // Ký số và xuất vé chính thức
      const ticketResult = await issueTicketOnPayment(conn, order, user);

      await conn.commit();

      // Đôn người tiếp theo trong hàng đợi ảo vào phòng thanh toán
      cleanAndPromoteQueue(order.event_id).catch(err => console.error('[QueuePromote] Error:', err.message));

      await logSecurityEvent({
        userId: order.user_id,
        action: 'PAYMENT_SUCCESS_WEBHOOK',
        severity: 'INFO',
        ipAddress: ip,
        userAgent,
        details: { orderId: order.id, orderCode, ticketId: ticketResult.ticketId }
      });

      res.json({
        success: true,
        message: 'Thanh toán thành công. Vé điện tử đã được ký số và cấp phát.',
        data: ticketResult
      });
    } catch (err) {
      await conn.rollback();
      console.error('[Payment Webhook] Error:', err);
      res.status(500).json({ success: false, error: 'Internal server error while processing webhook.' });
    } finally {
      conn.release();
    }
  } catch (error) {
    console.error('[Payment Webhook] Outer error:', error);
    res.status(500).json({ success: false, error: 'Error processing webhook.' });
  }
});

/**
 * 3b. Webhook dành riêng cho SePay (Tự động gạch nợ từ biến động số dư ngân hàng)
 * POST /api/payment/sepay-webhook
 * Định dạng payload SePay: { id, gateway, accountNumber, content, transferType, transferAmount, ... }
 */
router.post('/sepay-webhook', async (req, res) => {
  const { ip, userAgent } = getClientInfo(req);
  const sepayData = req.body;

  console.log('[SePay Webhook] Received payload:', JSON.stringify(sepayData));

  // 1. Kiểm tra xác thực HMAC-SHA256 (Khuyến nghị của SePay)
  const sepaySigHeader = req.headers['x-sepay-signature'];
  const sepayTimestamp = req.headers['x-sepay-timestamp'];
  const webhookSecret = process.env.SEPAY_WEBHOOK_SECRET || process.env.PAYMENT_WEBHOOK_SECRET || 'SecureTicket_Payment_Webhook_HMAC_Secret_2026';

  if (sepaySigHeader && sepayTimestamp) {
    // Chống Replay Attack: kiểm tra timestamp không được lệch quá 5 phút (300 giây)
    const currentTime = Math.floor(Date.now() / 1000);
    const reqTime = parseInt(sepayTimestamp, 10);
    if (Math.abs(currentTime - reqTime) > 300) {
      await logSecurityEvent({
        action: 'SEPAY_WEBHOOK_REPLAY_ATTACK',
        severity: 'HIGH',
        ipAddress: ip,
        userAgent,
        details: { currentTime, reqTime }
      });
      return res.status(401).json({ success: false, error: 'Webhook timestamp expired (Replay Attack blocked).' });
    }

    // Tính toán lại chữ ký HMAC-SHA256 theo chuẩn SePay: {timestamp}.{raw_body}
    const rawBody = req.rawBody || JSON.stringify(req.body);
    const dataToSign = `${sepayTimestamp}.${rawBody}`;
    const computedHash = crypto.createHmac('sha256', webhookSecret).update(dataToSign).digest('hex');
    const expectedSig = `sha256=${computedHash}`;

    // So sánh an toàn chống Timing Attack
    const sigBuf = Buffer.from(sepaySigHeader);
    const expBuf = Buffer.from(expectedSig);

    if (sigBuf.length !== expBuf.length || !crypto.timingSafeEqual(sigBuf, expBuf)) {
      await logSecurityEvent({
        action: 'SEPAY_WEBHOOK_INVALID_SIGNATURE',
        severity: 'CRITICAL',
        ipAddress: ip,
        userAgent,
        details: { received: sepaySigHeader, expected: expectedSig }
      });
      return res.status(401).json({ success: false, error: 'Chữ ký HMAC-SHA256 không hợp lệ!' });
    }
    console.log('[SePay Webhook] HMAC-SHA256 signature verified successfully!');
  } else {
    // 2. Tùy chọn kiểm tra API Key nếu người dùng cấu hình dạng API Key
    const expectedApiKey = process.env.SEPAY_API_KEY;
    if (expectedApiKey) {
      const authHeader = req.headers['authorization'] || '';
      if (!authHeader.includes(expectedApiKey)) {
        await logSecurityEvent({
          action: 'SEPAY_WEBHOOK_UNAUTHORIZED',
          severity: 'WARNING',
          ipAddress: ip,
          userAgent,
          details: { receivedAuth: authHeader }
        });
        return res.status(401).json({ success: false, error: 'Unauthorized SePay API Key' });
      }
    }
  }

  // Kiểm tra loại giao dịch: chỉ xử lý "in" (tiền vào)
  if (sepayData.transferType && sepayData.transferType.toLowerCase() !== 'in') {
    return res.json({ success: true, message: 'Bỏ qua giao dịch tiền ra (out).' });
  }

  // Trích xuất mã đơn hàng từ nội dung chuyển khoản (Ví dụ: "SECURE 255297" hoặc "SECURE255297")
  const content = (sepayData.content || sepayData.description || '').toUpperCase();
  const match = content.match(/SECURE\s*([0-9A-Z]{4,10})/);
  let orderCode = match ? match[1] : null;

  // Nếu không khớp regex SECURE, thử tìm mã số 6 chữ số
  if (!orderCode) {
    const numMatch = content.match(/\b\d{6}\b/);
    if (numMatch) {
      orderCode = numMatch[0];
    }
  }

  if (!orderCode) {
    console.warn('[SePay Webhook] Không tìm thấy mã đơn hàng trong nội dung:', content);
    return res.json({ success: false, message: 'Nội dung chuyển khoản không chứa mã đơn hàng hợp lệ.' });
  }

  const transferAmount = Number(sepayData.transferAmount || 0);

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    const [orderRows] = await conn.execute(
      'SELECT * FROM orders WHERE order_code = ? FOR UPDATE',
      [orderCode]
    );

    if (orderRows.length === 0) {
      await conn.rollback();
      console.warn(`[SePay Webhook] Không tìm thấy đơn hàng với mã: ${orderCode}`);
      return res.json({ success: false, message: `Không tìm thấy mã đơn hàng ${orderCode}` });
    }

    const order = orderRows[0];

    if (order.status === 'PAID') {
      await conn.rollback();
      return res.json({ success: true, message: 'Đơn hàng này đã được thanh toán trước đó.' });
    }

    if (order.status === 'EXPIRED' || order.status === 'CANCELLED') {
      await conn.rollback();
      return res.json({ success: false, message: `Đơn hàng đã ở trạng thái ${order.status}.` });
    }

    // Kiểm tra số tiền chuyển khoản
    if (transferAmount < Number(order.amount)) {
      await conn.rollback();
      console.warn(`[SePay Webhook] Số tiền không đủ: chuyển ${transferAmount}, cần ${order.amount}`);
      return res.json({ success: false, message: 'Số tiền chuyển khoản không đủ.' });
    }

    // Lấy thông tin người mua vé
    const [userRows] = await conn.execute(
      'SELECT id, email, device_public_key FROM users WHERE id = ?',
      [order.user_id]
    );
    const user = userRows[0];

    // Ký số ECDSA P-256 và xuất vé chính thức
    const ticketResult = await issueTicketOnPayment(conn, order, user);

    await conn.commit();

    // Tự động đôn người tiếp theo trong phòng chờ ảo
    cleanAndPromoteQueue(order.event_id).catch(err => console.error('[QueuePromote] Error:', err.message));

    await logSecurityEvent({
      userId: order.user_id,
      action: 'SEPAY_PAYMENT_SUCCESS',
      severity: 'INFO',
      ipAddress: ip,
      userAgent,
      details: {
        orderId: order.id,
        orderCode,
        transferAmount,
        ticketId: ticketResult.id || ticketResult.ticketId,
        sepayTransactionId: sepayData.id
      }
    });

    console.log(`[SePay Webhook] Thanh toán thành công cho đơn #${orderCode}, đã xuất vé ${ticketResult.ticket_code || ticketResult.ticketCode}`);

    // SePay yêu cầu trả về HTTP 200 JSON { success: true }
    return res.status(200).json({
      success: true,
      message: 'Xác thực thanh toán SePay thành công. Vé đã được ký số và cấp phát.',
      data: ticketResult
    });
  } catch (err) {
    await conn.rollback();
    console.error('[SePay Webhook] Error:', err);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  } finally {
    conn.release();
  }
});

/**
 * 4. Giả lập thanh toán tức thì (Dành cho Demo / Báo cáo đồ án / Sandbox Test)
 * POST /api/payment/simulate-transfer
 * Người dùng hoặc giảng viên bấm nút "Giả lập quét mã và chuyển khoản" trên app hoặc Postman
 */
router.post('/simulate-transfer', authenticateToken, async (req, res) => {
  const { orderId, orderCode } = req.body;
  const { ip, userAgent } = getClientInfo(req);

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    let query = 'SELECT * FROM orders WHERE ';
    let params = [];
    if (orderId) {
      query += 'id = ? FOR UPDATE';
      params.push(orderId);
    } else if (orderCode) {
      query += 'order_code = ? FOR UPDATE';
      params.push(orderCode);
    } else {
      await conn.rollback();
      return res.status(400).json({ success: false, error: 'orderId or orderCode required.' });
    }

    const [orders] = await conn.execute(query, params);
    if (orders.length === 0) {
      await conn.rollback();
      return res.status(404).json({ success: false, error: 'Order not found.' });
    }

    const order = orders[0];

    if (order.status === 'PAID') {
      await conn.rollback();
      return res.json({ success: true, message: 'Đơn hàng đã được thanh toán trước đó.' });
    }

    const [userRows] = await conn.execute(
      'SELECT id, email, device_public_key FROM users WHERE id = ?',
      [order.user_id]
    );
    const user = userRows[0];

    // Xuất vé an toàn
    const ticketResult = await issueTicketOnPayment(conn, order, user);

    await conn.commit();

    // Kích hoạt hàng đợi đôn người tiếp theo
    cleanAndPromoteQueue(order.event_id).catch(err => console.error('[QueuePromote] Error:', err.message));

    await logSecurityEvent({
      userId: order.user_id,
      action: 'PAYMENT_SIMULATED_SUCCESS',
      severity: 'INFO',
      ipAddress: ip,
      userAgent,
      details: { orderId: order.id, orderCode: order.order_code, ticketId: ticketResult.id || ticketResult.ticketId }
    });

    res.json({
      success: true,
      message: '⚡ [Sandbox] Giả lập nhận tiền thành công! Vé đã được ký số và đưa vào ví.',
      data: ticketResult
    });
  } catch (err) {
    await conn.rollback();
    console.error('[Payment Simulate] Error:', err);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  } finally {
    conn.release();
  }
});

export default router;

