import { pool } from '../config/db.js';

/**
 * Ghi log an toàn hệ thống (Audit Trail) chống chối bỏ
 */
export async function logSecurityEvent({
  userId = null,
  action,
  severity = 'INFO',
  ipAddress = null,
  userAgent = null,
  details = null
}) {
  try {
    const detailsJson = details ? JSON.stringify(details) : null;
    await pool.execute(
      `INSERT INTO security_audit_logs (user_id, action, severity, ip_address, user_agent, details)
       VALUES (?, ?, ?, ?, ?, ?)`,
      [userId, action, severity, ipAddress, userAgent, detailsJson]
    );
  } catch (err) {
    console.error('[AuditLog] Failed to log event:', err.message);
  }
}

