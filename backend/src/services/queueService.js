import { v4 as uuidv4 } from 'uuid';
import jwt from 'jsonwebtoken';
import { pool } from '../config/db.js';

// Cấu hình số người tối đa được vào trang thanh toán cùng một lúc (VD: 100 người)
export const MAX_CONCURRENT_CHECKOUTS = 100;

// Thời gian tối đa được giữ chỗ thanh toán (5 phút = 300 giây)
export const CHECKOUT_TTL_SECONDS = 300;

const QUEUE_PASS_SECRET = process.env.QUEUE_PASS_SECRET || 'SecureTicket_QueuePassSecret_2026!#Key';

/**
 * Tự động giải phóng các lượt thanh toán đã quá hạn (5 phút mà không thanh toán)
 * và đôn (promote) những người đang xếp hàng vào lượt tiếp theo theo thứ tự FIFO
 */
export async function cleanAndPromoteQueue(eventId) {
  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    // 1. Quét và đánh dấu EXPIRED cho các phiên thanh toán đã quá hạn 5 phút
    await conn.execute(
      `UPDATE checkout_queue 
       SET status = 'EXPIRED' 
       WHERE event_id = ? AND status = 'ACTIVE' AND expires_at < NOW()`,
      [eventId]
    );

    // 2. Đếm số lượng người hiện đang trong trang thanh toán (ACTIVE)
    const [activeRows] = await conn.execute(
      `SELECT COUNT(*) as activeCount 
       FROM checkout_queue 
       WHERE event_id = ? AND status = 'ACTIVE' AND expires_at >= NOW()`,
      [eventId]
    );
    const currentActive = activeRows[0].activeCount;
    const availableSlots = Math.max(0, MAX_CONCURRENT_CHECKOUTS - currentActive);

    // 3. Nếu còn chỗ trống, đôn những người đang xếp hàng (WAITING) lên (ACTIVE) theo thứ tự FIFO
    if (availableSlots > 0) {
      const [waitingUsers] = await conn.execute(
        `SELECT id, user_id, queue_number 
         FROM checkout_queue 
         WHERE event_id = ? AND status = 'WAITING' 
         ORDER BY queue_number ASC 
         LIMIT ? FOR UPDATE`,
        [eventId, availableSlots]
      );

      for (const item of waitingUsers) {
        const passToken = jwt.sign(
          {
            queueId: item.id,
            eventId: eventId,
            userId: item.user_id,
            type: 'CHECKOUT_PASS'
          },
          QUEUE_PASS_SECRET,
          { expiresIn: `${CHECKOUT_TTL_SECONDS}s` }
        );

        await conn.execute(
          `UPDATE checkout_queue 
           SET status = 'ACTIVE', 
               admitted_at = NOW(), 
               expires_at = DATE_ADD(NOW(), INTERVAL ? SECOND), 
               pass_token = ? 
           WHERE id = ?`,
          [CHECKOUT_TTL_SECONDS, passToken, item.id]
        );
      }
    }

    await conn.commit();
  } catch (err) {
    await conn.rollback();
    console.error('[QueueService] Error during cleanAndPromoteQueue:', err.message);
  } finally {
    conn.release();
  }
}

/**
 * Xác thực Pass Token để cho phép mua vé
 */
export function verifyCheckoutPass(token, eventId, userId) {
  try {
    const decoded = jwt.verify(token, QUEUE_PASS_SECRET);
    if (decoded.type !== 'CHECKOUT_PASS') return false;
    if (decoded.eventId !== eventId || decoded.userId !== userId) return false;
    return decoded;
  } catch (e) {
    return false;
  }
}

