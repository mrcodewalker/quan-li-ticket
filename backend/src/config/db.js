import mysql from 'mysql2/promise';
import dotenv from 'dotenv';

dotenv.config();

export const pool = mysql.createPool({
  host: process.env.DB_HOST || '42.96.5.186',
  port: parseInt(process.env.DB_PORT || '3306'),
  user: process.env.DB_USER || 'anonymous',
  password: process.env.DB_PASSWORD || 'Haibeo2004@',
  database: process.env.DB_NAME || 'secure_ticket',
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0,
  connectTimeout: 30000,
  enableKeepAlive: true,
  keepAliveInitialDelay: 10000
});

export async function testDbConnection() {
  try {
    const connection = await pool.getConnection();
    console.log('[DB] Successfully connected to MySQL at ' + (process.env.DB_HOST || '42.96.5.186'));
    connection.release();
    return true;
  } catch (error) {
    console.error('[DB] Connection error:', error.message);
    throw error;
  }
}
