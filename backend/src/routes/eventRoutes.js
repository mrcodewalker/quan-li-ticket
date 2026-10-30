import express from 'express';
import { v4 as uuidv4 } from 'uuid';
import { pool } from '../config/db.js';
import { authenticateToken, authorizeRoles, getClientInfo } from '../middlewares/authMiddleware.js';
import { logSecurityEvent } from '../services/auditService.js';

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

