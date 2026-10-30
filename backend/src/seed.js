import bcrypt from 'bcryptjs';
import { v4 as uuidv4 } from 'uuid';
import { pool } from './config/db.js';

async function seedData() {
  console.log('[Seed] Seeding sample accounts and events...');
  try {
    const passwordHash = await bcrypt.hash('SecureTicket@2026', 12);

    const organizerId = uuidv4();
    const staffId = uuidv4();
    const customerId = uuidv4();

    // Insert sample users if not already existing
    await pool.execute(
      `INSERT INTO users (id, email, password_hash, full_name, phone, role)
       VALUES (?, 'organizer@secureticket.com', ?, 'Ban Tổ Chức SecureTicket', '0901234567', 'ORGANIZER')
       ON DUPLICATE KEY UPDATE full_name = VALUES(full_name)`,
      [organizerId, passwordHash]
    );

    await pool.execute(
      `INSERT INTO users (id, email, password_hash, full_name, phone, role)
       VALUES (?, 'staff@secureticket.com', ?, 'Nhân Viên Soát Vé 01', '0912345678', 'STAFF')
       ON DUPLICATE KEY UPDATE full_name = VALUES(full_name)`,
      [staffId, passwordHash]
    );

    await pool.execute(
      `INSERT INTO users (id, email, password_hash, full_name, phone, role)
       VALUES (?, 'customer@secureticket.com', ?, 'Nguyễn Văn An (Khách Hàng)', '0987654321', 'CUSTOMER')
       ON DUPLICATE KEY UPDATE full_name = VALUES(full_name)`,
      [customerId, passwordHash]
    );

    // Retrieve the actual organizer ID in case it already existed
    const [orgRows] = await pool.execute("SELECT id FROM users WHERE email = 'organizer@secureticket.com'");
    const actualOrgId = orgRows[0].id;

    // Insert sample events
    const event1Id = uuidv4();
    const event2Id = uuidv4();

    const [eventsExist] = await pool.execute('SELECT COUNT(*) as count FROM events');
    if (eventsExist[0].count === 0) {
      await pool.execute(
        `INSERT INTO events (id, title, description, location, start_time, end_time, organizer_id, total_tickets, available_tickets, ticket_price, status)
         VALUES (?, 'SecureFest 2026 - Cyber Security Summit & Concert', 'Sự kiện âm nhạc và công nghệ kết hợp với hệ thống vé an ninh cao', 'Trung tâm Hội nghị Quốc gia, Hà Nội', '2026-11-15 19:00:00', '2026-11-15 23:00:00', ?, 500, 500, 250000.00, 'UPCOMING')`,
        [event1Id, actualOrgId]
      );

      await pool.execute(
        `INSERT INTO events (id, title, description, location, start_time, end_time, organizer_id, total_tickets, available_tickets, ticket_price, status)
         VALUES (?, 'Hội nghị An toàn & Bảo mật Phần mềm KMA 2026', 'Workshop chuyên đề về bảo mật thiết bị di động, mã hóa Keystore và NFC HCE', 'Học viện Kỹ thuật Mật mã, Hà Nội', '2026-12-01 08:30:00', '2026-12-01 17:00:00', ?, 200, 200, 0.00, 'UPCOMING')`,
        [event2Id, actualOrgId]
      );
      console.log('[Seed] Created sample events.');
    } else {
      console.log('[Seed] Events already exist, skipping event seed.');
    }

    console.log('[Seed] Done! Sample login credentials:');
    console.log('   - Organizer: organizer@secureticket.com / SecureTicket@2026');
    console.log('   - Staff:     staff@secureticket.com / SecureTicket@2026');
    console.log('   - Customer:  customer@secureticket.com / SecureTicket@2026');
    process.exit(0);
  } catch (err) {
    console.error('[Seed] Error:', err);
    process.exit(1);
  }
}

seedData();

