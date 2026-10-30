import { pool } from './config/db.js';

async function createTable() {
  try {
    await pool.execute(`
      ALTER TABLE checkout_queue MODIFY COLUMN pass_token TEXT NULL;
    `);
    console.log('pass_token altered to TEXT successfully!');
    process.exit(0);
  } catch (err) {
    console.error('Error modifying table:', err);
    process.exit(1);
  }
}

createTable();
