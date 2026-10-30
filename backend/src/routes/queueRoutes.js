import express from 'express';
import { v4 as uuidv4 } from 'uuid';
import jwt from 'jsonwebtoken';
import { pool } from '../config/db.js';
import { authenticateToken } from '../middlewares/authMiddleware.js';
import {
  cleanAndPromoteQueue,
  MAX_CONCURRENT_CHECKOUTS,
  CHECKOUT_TTL_SECONDS
} from '../services/queueService.js';

const router = express.Router();
const QUEUE_PASS_SECRET = process.env.QUEUE_PASS_SECRET || 'SecureTicket_QueuePassSecret_2026!#Key';

/**
 * Người dùng đăng ký vào phòng chờ mua vé (Flash Sale Virtual Waiting Room)
 */
router.post('/join', authenticateToken, async (req, res) => {
  const { eventId, customSlotLimit } = req.body;
  const effectiveLimit = parseInt(customSlotLimit) || MAX_CONCURRENT_CHECKOUTS;

  if (!eventId) {
    return res.status(400).json({ success: false, error: 'eventId is required.' });
  }

  try {
    // 1. Kiểm tra sự kiện có tồn tại không
    const [eventRows] = await pool.execute('SELECT id, title, available_tickets, status FROM events WHERE id = ?', [eventId]);
    if (eventRows.length === 0) {
      return res.status(404).json({ success: false, error: 'Event not found.' });
    }

    // 2. Dọn dẹp các phiên quá hạn và đôn hàng đợi
    await cleanAndPromoteQueue(eventId);

    // 3. Kiểm tra xem người dùng này đã có phiên ACTIVE còn hạn hay không
    const [existingActive] = await pool.execute(
      `SELECT id, pass_token, TIMESTAMPDIFF(SECOND, NOW(), expires_at) as remainingSeconds 
       FROM checkout_queue 
       WHERE event_id = ? AND user_id = ? AND status = 'ACTIVE' AND expires_at > NOW()`,
      [eventId, req.user.id]
    );

    if (existingActive.length > 0) {
      return res.json({
        success: true,
        message: 'Bạn đã có quyền vào thanh toán.',
        data: {
          queueId: existingActive[0].id,
          status: 'ACTIVE',
          passToken: existingActive[0].pass_token,
          expiresInSeconds: existingActive[0].remainingSeconds
        }
      });
    }

    // 4. Đếm số lượng phiên ACTIVE hiện tại
    const [countRows] = await pool.execute(
      `SELECT COUNT(*) as activeCount 
       FROM checkout_queue 
       WHERE event_id = ? AND status = 'ACTIVE' AND expires_at >= NOW()`,
      [eventId]
    );

    const activeCount = countRows[0].activeCount;

    // 5. Nếu còn chỗ (activeCount < effectiveLimit) -> Cấp quyền vào ngay
    if (activeCount < effectiveLimit) {
      const queueId = uuidv4();
      const passToken = jwt.sign(
        {
          queueId,
          eventId,
          userId: req.user.id,
          type: 'CHECKOUT_PASS'
        },
        QUEUE_PASS_SECRET,
        { expiresIn: `${CHECKOUT_TTL_SECONDS}s` }
      );

      await pool.execute(
        `INSERT INTO checkout_queue (id, event_id, user_id, status, queue_number, admitted_at, expires_at, pass_token)
         VALUES (?, ?, ?, 'ACTIVE', 0, NOW(), DATE_ADD(NOW(), INTERVAL ? SECOND), ?)`,
        [queueId, eventId, req.user.id, CHECKOUT_TTL_SECONDS, passToken]
      );

      return res.json({
        success: true,
        message: 'Đã cấp quyền vào thanh toán vé trực tiếp!',
        data: {
          queueId,
          status: 'ACTIVE',
          passToken,
          expiresInSeconds: CHECKOUT_TTL_SECONDS
        }
      });
    }

    // 6. Nếu đã đầy người (>= 100 người) -> Xếp vào hàng đợi (WAITING)
    const [maxQueue] = await pool.execute(
      'SELECT COALESCE(MAX(queue_number), 0) + 1 as nextNumber FROM checkout_queue WHERE event_id = ?',
      [eventId]
    );
    const nextQueueNumber = maxQueue[0].nextNumber;
    const queueId = uuidv4();

    await pool.execute(
      `INSERT INTO checkout_queue (id, event_id, user_id, status, queue_number)
       VALUES (?, ?, ?, 'WAITING', ?)`,
      [queueId, eventId, req.user.id, nextQueueNumber]
    );

    // Đếm số người xếp hàng trước mình
    const [aheadRows] = await pool.execute(
      `SELECT COUNT(*) as aheadCount 
       FROM checkout_queue 
       WHERE event_id = ? AND status = 'WAITING' AND queue_number < ?`,
      [eventId, nextQueueNumber]
    );
    const usersAhead = aheadRows[0].aheadCount;

    res.json({
      success: true,
      message: 'Hệ thống đang mở bán lượng lớn. Bạn đang được xếp vào hàng đợi mua vé.',
      data: {
        queueId,
        status: 'WAITING',
        queueNumber: nextQueueNumber,
        usersAhead,
        estimatedWaitSeconds: (usersAhead + 1) * 30
      }
    });
  } catch (error) {
    console.error('[Queue] Join queue error:', error);
    res.status(500).json({ success: false, error: 'Internal server error while joining queue.' });
  }
});

/**
 * Kiểm tra trạng thái xếp hàng của người dùng (Polling mỗi 3-5 giây)
 */
router.get('/status/:queueId', authenticateToken, async (req, res) => {
  const { queueId } = req.params;

  try {
    const [rows] = await pool.execute('SELECT * FROM checkout_queue WHERE id = ?', [queueId]);
    if (rows.length === 0) {
      return res.status(404).json({ success: false, error: 'Queue record not found.' });
    }

    const item = rows[0];

    // Chạy promote nếu có slot trống
    await cleanAndPromoteQueue(item.event_id);

    // Lấy lại dữ liệu sau khi promote
    const [refreshedRows] = await pool.execute(
      `SELECT id, status, queue_number, pass_token, 
              TIMESTAMPDIFF(SECOND, NOW(), expires_at) as remainingSeconds 
       FROM checkout_queue WHERE id = ?`,
      [queueId]
    );
    const refreshed = refreshedRows[0];

    if (refreshed.status === 'ACTIVE') {
      return res.json({
        success: true,
        data: {
          queueId: refreshed.id,
          status: 'ACTIVE',
          passToken: refreshed.pass_token,
          expiresInSeconds: Math.max(0, refreshed.remainingSeconds || 0)
        }
      });
    }

    if (refreshed.status === 'WAITING') {
      const [aheadRows] = await pool.execute(
        `SELECT COUNT(*) as aheadCount 
         FROM checkout_queue 
         WHERE event_id = ? AND status = 'WAITING' AND queue_number < ?`,
        [item.event_id, refreshed.queue_number]
      );
      const usersAhead = aheadRows[0].aheadCount;

      return res.json({
        success: true,
        data: {
          queueId: refreshed.id,
          status: 'WAITING',
          queueNumber: refreshed.queue_number,
          usersAhead,
          estimatedWaitSeconds: (usersAhead + 1) * 30
        }
      });
    }

    // COMPLETED / EXPIRED / CANCELLED
    res.json({
      success: true,
      data: {
        queueId: refreshed.id,
        status: refreshed.status
      }
    });
  } catch (error) {
    console.error('[Queue] Status check error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

/**
 * Hủy lượt hoặc rời khỏi hàng đợi để nhường chỗ cho người tiếp theo
 */
router.post('/leave', authenticateToken, async (req, res) => {
  const { queueId } = req.body;
  if (!queueId) {
    return res.status(400).json({ success: false, error: 'queueId is required.' });
  }

  try {
    const [rows] = await pool.execute('SELECT event_id FROM checkout_queue WHERE id = ? AND user_id = ?', [queueId, req.user.id]);
    if (rows.length > 0) {
      await pool.execute(`UPDATE checkout_queue SET status = 'CANCELLED' WHERE id = ?`, [queueId]);
      await cleanAndPromoteQueue(rows[0].event_id);
    }

    res.json({ success: true, message: 'Đã rời hàng đợi.' });
  } catch (error) {
    console.error('[Queue] Leave error:', error);
    res.status(500).json({ success: false, error: 'Internal server error.' });
  }
});

export default router;

