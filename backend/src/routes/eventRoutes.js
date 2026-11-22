import express from 'express';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import { pool } from '../config/db.js';
import { authenticateToken, authorizeRoles, getClientInfo } from '../middlewares/authMiddleware.js';
import { logSecurityEvent } from '../services/auditService.js';
import {
  ZONE_TIERS,
  SEAT_NUMBERS,
  MAX_ACTIVE_TICKETS_PER_EVENT,
  computeTierPrice,
  parseTicketSeat
} from '../config/ticketTiers.js';

const router = express.Router();

/**
 * Lấy danh sách tất cả sự kiện mở bán
 */
router.get('/', async (req, res) => {
  try {
    const [events] = await pool.execute(`
      SELECT e.id, e.title, e.description, e.location, e.start_time, e.end_time,
             e.total_tickets, e.available_tickets, e.ticket_price, e.status, e.created_at,
             u.full_name as organizer_name
      FROM events e
      LEFT JOIN users u ON e.organizer_id = u.id
      ORDER BY e.start_time ASC
    `);

    res.json({
      success: true,
      data: events
    });
  } catch (error) {
    console.error('[Events] Fetch error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

/**
 * Dashboard tổng hợp cho Ban Tổ Chức (RBAC: ORGANIZER, ADMIN)
 * GET /api/events/organizer/dashboard
 */
router.get('/organizer/dashboard', authenticateToken, authorizeRoles('ORGANIZER', 'ADMIN'), async (req, res) => {
  try {
    const [revRows] = await pool.execute(
      `SELECT COALESCE(SUM(amount), 0) as totalRevenue, COUNT(*) as paidOrders
       FROM orders WHERE status = 'PAID'`
    );
    const [tktRows] = await pool.execute(
      `SELECT
         COUNT(*) as totalIssued,
         SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END) as activeTickets,
         SUM(CASE WHEN status = 'USED' THEN 1 ELSE 0 END) as checkedInTickets,
         SUM(CASE WHEN status = 'REVOKED' THEN 1 ELSE 0 END) as revokedTickets
       FROM tickets`
    );
    const [alertRows] = await pool.execute(
      `SELECT COUNT(*) as securityAlerts
       FROM security_audit_logs
       WHERE severity IN ('CRITICAL', 'HIGH', 'WARNING')`
    );
    const [events] = await pool.execute(
      `SELECT e.id, e.title, e.location, e.start_time, e.total_tickets, e.available_tickets,
              e.ticket_price, e.status,
              (SELECT COUNT(*) FROM tickets t WHERE t.event_id = e.id) as issued_count,
              (SELECT COUNT(*) FROM tickets t WHERE t.event_id = e.id AND t.status = 'USED') as used_count
       FROM events e
       ORDER BY e.start_time ASC`
    );
    const [recentTickets] = await pool.execute(
      `SELECT t.id, t.ticket_code, t.status, t.issued_at,
              e.title as event_title, u.full_name as owner_name, u.email as owner_email
       FROM tickets t
       JOIN events e ON t.event_id = e.id
       JOIN users u ON t.current_owner_id = u.id
       ORDER BY t.issued_at DESC
       LIMIT 25`
    );

    // Mask email để bảo vệ PII khách hàng trên bảng quản trị
    const maskedTickets = recentTickets.map(t => {
      const parts = String(t.owner_email || '').split('@');
      const maskedEmail = parts.length === 2
        ? `${parts[0].slice(0, 3)}***@${parts[1]}`
        : '***';
      return {
        ...t,
        owner_email: maskedEmail
      };
    });

    res.json({
      success: true,
      data: {
        summary: {
          totalRevenue: Number(revRows[0]?.totalRevenue || 0),
          paidOrders: Number(revRows[0]?.paidOrders || 0),
          totalIssued: Number(tktRows[0]?.totalIssued || 0),
          activeTickets: Number(tktRows[0]?.activeTickets || 0),
          checkedInTickets: Number(tktRows[0]?.checkedInTickets || 0),
          revokedTickets: Number(tktRows[0]?.revokedTickets || 0),
          securityAlerts: Number(alertRows[0]?.securityAlerts || 0)
        },
        events,
        recentTickets: maskedTickets
      }
    });
  } catch (error) {
    console.error('[Events] Organizer dashboard error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

/**
 * Nhật ký An ninh SIEM cho Ban Tổ Chức (RBAC: ORGANIZER, ADMIN)
 * GET /api/events/organizer/audit-logs
 */
router.get('/organizer/audit-logs', authenticateToken, authorizeRoles('ORGANIZER', 'ADMIN'), async (req, res) => {
  try {
    const [logs] = await pool.execute(
      `SELECT id, user_id, action, severity, ip_address, details, created_at
       FROM security_audit_logs
       ORDER BY created_at DESC
       LIMIT 40`
    );
    res.json({ success: true, data: logs });
  } catch (error) {
    console.error('[Events] Audit logs error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

/**
 * Cập nhật trạng thái đóng/mở bán sự kiện (RBAC: ORGANIZER, ADMIN)
 * PUT /api/events/:id/status
 */
router.put('/:id/status', authenticateToken, authorizeRoles('ORGANIZER', 'ADMIN'), async (req, res) => {
  const { id } = req.params;
  const { status } = req.body || {};
  const { ip, userAgent } = getClientInfo(req);

  if (!['UPCOMING', 'ONGOING', 'COMPLETED', 'CANCELLED'].includes(status)) {
    return res.status(400).json({ success: false, error: 'Invalid event status.' });
  }

  try {
    await pool.execute('UPDATE events SET status = ? WHERE id = ?', [status, id]);
    await logSecurityEvent({
      userId: req.user.id,
      action: 'EVENT_STATUS_UPDATED',
      severity: 'INFO',
      ipAddress: ip,
      userAgent,
      details: { eventId: id, status }
    });
    res.json({ success: true, message: `Đã cập nhật trạng thái sự kiện sang ${status}.` });
  } catch (error) {
    console.error('[Events] Update status error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

/**
 * Lấy ngữ cảnh đặt vé & sơ đồ ghế thời gian thực (6 Zone Tiers + ghế đã bán/đang giữ + hạn mức X/6)
 * GET /api/events/:id/booking-context
 */
router.get('/:id/booking-context', async (req, res) => {
  const { id } = req.params;

  // Optional auth parsing: nếu client gửi Bearer token hợp lệ thì trả về hạn mức cá nhân;
  // nếu token hết hạn trả 401 để OkHttp TokenAuthenticator tự động refresh
  let authUserId = null;
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];
  if (token && process.env.JWT_SECRET) {
    try {
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      authUserId = decoded?.id || null;
    } catch (err) {
      return res.status(401).json({ success: false, error: 'Invalid or expired access token.' });
    }
  }

  try {
    const [eventRows] = await pool.execute(
      `SELECT e.*, u.full_name as organizer_name
       FROM events e
       LEFT JOIN users u ON e.organizer_id = u.id
       WHERE e.id = ?`,
      [id]
    );

    if (eventRows.length === 0) {
      return res.status(404).json({ success: false, error: 'Event not found.' });
    }

    const event = eventRows[0];

    const [ticketRows] = await pool.execute(
      `SELECT ticket_code FROM tickets WHERE event_id = ? AND status IN ('ACTIVE', 'USED')`,
      [id]
    );

    const [pendingOrderRows] = await pool.execute(
      `SELECT seat_zone, seat_number FROM orders
       WHERE event_id = ? AND status = 'PENDING' AND expires_at > NOW() AND seat_zone IS NOT NULL AND seat_number IS NOT NULL`,
      [id]
    );

    const soldSeats = [];
    const soldSet = new Set();
    for (const row of ticketRows) {
      const parsed = parseTicketSeat(row.ticket_code);
      if (parsed.seatZone && parsed.seatNumber) {
        const key = `${parsed.seatZone}${parsed.seatNumber}`;
        if (!soldSet.has(key)) {
          soldSet.add(key);
          soldSeats.push(key);
        }
      }
    }

    const heldSeats = [];
    const heldSet = new Set();
    for (const row of pendingOrderRows) {
      const key = `${String(row.seat_zone).toUpperCase()}${row.seat_number}`;
      if (!soldSet.has(key) && !heldSet.has(key)) {
        heldSet.add(key);
        heldSeats.push(key);
      }
    }

    let userActiveTickets = 0;
    let userPendingOrders = 0;
    if (authUserId) {
      const [tktCnt] = await pool.execute(
        `SELECT COUNT(*) as cnt FROM tickets WHERE current_owner_id = ? AND event_id = ? AND status = 'ACTIVE'`,
        [authUserId, id]
      );
      const [ordCnt] = await pool.execute(
        `SELECT COUNT(*) as cnt FROM orders WHERE user_id = ? AND event_id = ? AND status = 'PENDING' AND expires_at > NOW()`,
        [authUserId, id]
      );
      userActiveTickets = Number(tktCnt[0]?.cnt || 0);
      userPendingOrders = Number(ordCnt[0]?.cnt || 0);
    }

    const zones = ZONE_TIERS.map(tier => {
      let takenInZone = 0;
      for (const seat of SEAT_NUMBERS) {
        const key = `${tier.code}${seat}`;
        if (soldSet.has(key) || heldSet.has(key)) {
          takenInZone++;
        }
      }
      return {
        code: tier.code,
        name: tier.name,
        multiplier: tier.multiplier,
        price: computeTierPrice(event.ticket_price, tier.multiplier),
        perks: tier.perks,
        totalSeats: SEAT_NUMBERS.length,
        availableSeats: Math.max(0, SEAT_NUMBERS.length - takenInZone),
        seatNumbers: SEAT_NUMBERS
      };
    });

    res.json({
      success: true,
      data: {
        event,
        maxTicketsPerEvent: MAX_ACTIVE_TICKETS_PER_EVENT,
        userActiveTickets,
        userPendingOrders,
        userTotalActive: userActiveTickets + userPendingOrders,
        zones,
        soldSeats,
        heldSeats
      }
    });
  } catch (error) {
    console.error('[Events] Booking context error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

/**
 * Lấy chi tiết một sự kiện
 */
router.get('/:id', async (req, res) => {
  const { id } = req.params;
  try {
    const [rows] = await pool.execute(
      `SELECT e.*, u.full_name as organizer_name, u.email as organizer_email
       FROM events e
       LEFT JOIN users u ON e.organizer_id = u.id
       WHERE e.id = ?`,
      [id]
    );

    if (rows.length === 0) {
      return res.status(404).json({ success: false, error: 'Event not found.' });
    }

    res.json({
      success: true,
      data: rows[0]
    });
  } catch (error) {
    console.error('[Events] Get event error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

/**
 * Tạo sự kiện mới (Chỉ dành cho ORGANIZER hoặc ADMIN)
 */
router.post('/', authenticateToken, authorizeRoles('ORGANIZER', 'ADMIN'), async (req, res) => {
  const { title, description, location, startTime, endTime, totalTickets, ticketPrice } = req.body;
  const { ip, userAgent } = getClientInfo(req);

  if (!title || !location || !startTime || !endTime) {
    return res.status(400).json({ success: false, error: 'Title, location, startTime and endTime are required.' });
  }

  const eventId = uuidv4();
  const ticketCount = parseInt(totalTickets) || 100;
  const price = parseFloat(ticketPrice) || 0.00;

  try {
    await pool.execute(
      `INSERT INTO events (id, title, description, location, start_time, end_time, organizer_id, total_tickets, available_tickets, ticket_price)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [eventId, title, description || '', location, new Date(startTime), new Date(endTime), req.user.id, ticketCount, ticketCount, price]
    );

    await logSecurityEvent({
      userId: req.user.id,
      action: 'EVENT_CREATED',
      severity: 'INFO',
      ipAddress: ip,
      userAgent,
      details: { eventId, title, totalTickets: ticketCount, price }
    });

    res.status(201).json({
      success: true,
      message: 'Event created successfully.',
      data: { eventId, title, totalTickets: ticketCount }
    });
  } catch (error) {
    console.error('[Events] Create event error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

export default router;

