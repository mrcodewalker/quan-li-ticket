import { pool } from './config/db.js';

async function createOrdersTable() {
  try {
    await pool.execute(`
      CREATE TABLE IF NOT EXISTS orders (
        id VARCHAR(36) PRIMARY KEY,
        order_code VARCHAR(32) NOT NULL UNIQUE,
        event_id VARCHAR(36) NOT NULL,
        user_id VARCHAR(36) NOT NULL,
        ticket_id VARCHAR(36) NULL,
        amount DECIMAL(12, 2) NOT NULL,
        status ENUM('PENDING', 'PAID', 'EXPIRED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
        payment_method VARCHAR(32) DEFAULT 'VIETQR',
        qr_image_url TEXT NULL,
        transfer_content VARCHAR(64) NOT NULL,
        expires_at DATETIME NOT NULL,
        paid_at DATETIME NULL,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
        INDEX idx_order_code (order_code),
        INDEX idx_user_orders (user_id)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    `);
    console.log('orders table created successfully in MySQL!');
    process.exit(0);
  } catch (err) {
    console.error('Error creating orders table:', err);
    process.exit(1);
  }
}

createOrdersTable();

