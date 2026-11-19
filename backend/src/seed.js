import bcrypt from 'bcryptjs';
import { v4 as uuidv4 } from 'uuid';
import { pool } from './config/db.js';
import {
  ensureKeysLoaded,
  signPayload,
  createTicketCanonicalString,
  generateSecureNonce,
  encryptCustomerPII
} from './services/cryptoService.js';

async function seedData() {
  console.log('[Seed] Upgrading schema & seeding realistic 2,000 VND events + diverse signed tickets...');
  try {
    ensureKeysLoaded();

    // 1. Mở rộng cột phone lên VARCHAR(255) để chứa bản mã AES-256-GCM PII
    try {
      await pool.execute('ALTER TABLE users MODIFY COLUMN phone VARCHAR(255) NULL');
    } catch (e) {
      console.warn('[Seed] Schema alter notice:', e.message);
    }

    const passwordHash = await bcrypt.hash('SecureTicket@2026', 12);

    const sampleUsers = [
      {
        email: 'organizer@secureticket.com',
        fullName: 'Ban Tổ Chức TicketShield VN',
        phone: '0901234567',
        role: 'ORGANIZER'
      },
      {
        email: 'staff@secureticket.com',
        fullName: 'Trần Quốc Bảo (Soát Vé Cổng VIP)',
        phone: '0912345678',
        role: 'STAFF'
      },
      {
        email: 'customer@secureticket.com',
        fullName: 'Nguyễn Minh Anh',
        phone: '0988765432',
        role: 'CUSTOMER'
      },
      {
        email: 'friend@example.com',
        fullName: 'Trần Hoàng Nam',
        phone: '0918273645',
        role: 'CUSTOMER'
      },
      {
        email: 'linh.pham@secureticket.com',
        fullName: 'Phạm Khánh Linh',
        phone: '0938475612',
        role: 'CUSTOMER'
      },
      {
        email: 'minhkhoa.le@gmail.com',
        fullName: 'Lê Minh Khoa',
        phone: '0976543219',
        role: 'CUSTOMER'
      }
    ];

    for (const u of sampleUsers) {
      const encPhone = encryptCustomerPII(u.phone);
      await pool.execute(
        `INSERT INTO users (id, email, password_hash, full_name, phone, role)
         VALUES (?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone), role = VALUES(role)`,
        [uuidv4(), u.email, passwordHash, u.fullName, encPhone, u.role]
      );
    }

    const [orgRows] = await pool.execute("SELECT id FROM users WHERE email = 'organizer@secureticket.com'");
    const actualOrgId = orgRows[0].id;

    const [custRows] = await pool.execute("SELECT id, device_public_key FROM users WHERE email = 'customer@secureticket.com'");
    const customer = custRows[0];

    // 2. Danh sách 10 sự kiện thực tế giá 2.000đ (2k) để test VietQR & SePay cực kỳ đa dạng
    const realisticEvents = [
      {
        title: 'Anh Trai Vượt Ngàn Chông Gai Concert - D-5',
        description: '[ÂM NHẠC] Đêm nhạc hội quy tụ 33 Anh Tài với sân khấu 360 độ, hệ thống vé ký số ECDSA chống phe vé và định danh phần cứng.',
        location: 'Vinhomes Ocean Park 3, Hà Nội',
        startTime: '2026-11-28 19:30:00',
        endTime: '2026-11-28 23:45:00',
        totalTickets: 800,
        availableTickets: 642,
        ticketPrice: 2000.00
      },
      {
        title: 'Anh Trai "Say Hi" Live Concert - Đêm Vũ Đạo Số',
        description: '[ÂM NHẠC] Đại nhạc hội thần tượng thế hệ mới tích hợp vòng tay NFC HCE và mã QR động TOTP xoay vòng 30 giây.',
        location: 'SVĐ Quốc gia Mỹ Đình, Hà Nội',
        startTime: '2026-12-05 19:00:00',
        endTime: '2026-12-05 23:30:00',
        totalTickets: 1000,
        availableTickets: 785,
        ticketPrice: 2000.00
      },
      {
        title: 'SecureFest 2026 - Cyber Security Summit & Concert',
        description: '[AN TOÀN TT] Sự kiện âm nhạc EDM và hội thảo An toàn thông tin kết hợp hệ thống vé Zero-Trust chống giả mạo.',
        location: 'Trung tâm Hội nghị Quốc gia (NCC), Hà Nội',
        startTime: '2026-11-25 19:00:00',
        endTime: '2026-11-25 23:00:00',
        totalTickets: 500,
        availableTickets: 468,
        ticketPrice: 2000.00
      },
      {
        title: 'Hội nghị An toàn & Bảo mật Phần mềm KMA 2026',
        description: '[AN TOÀN TT] Chuyên đề bảo mật ứng dụng di động, AndroidKeyStore StrongBox TEE, chống dịch ngược RASP và NFC HCE.',
        location: 'Học viện Kỹ thuật Mật mã, Hà Nội',
        startTime: '2026-12-01 08:30:00',
        endTime: '2026-12-01 17:00:00',
        totalTickets: 300,
        availableTickets: 245,
        ticketPrice: 2000.00
      },
      {
        title: 'Hà Anh Tuấn Live Concert: Chân Trời Rực Rỡ',
        description: '[LIVE SHOW] Đêm nhạc giao hưởng thính phòng đặc biệt, áp dụng kiểm soát vé định danh chống đầu cơ chợ đen.',
        location: 'Nhà hát Hồ Gươm, Hoàn Kiếm, Hà Nội',
        startTime: '2026-12-12 20:00:00',
        endTime: '2026-12-12 23:15:00',
        totalTickets: 450,
        availableTickets: 310,
        ticketPrice: 2000.00
      },
      {
        title: 'Đen Vâu - Show Của Đen: Đồng Âm Hội Ngộ',
        description: '[ÂM NHẠC] Live concert kỷ niệm hành trình Hip-hop & Indie cùng cộng đồng Đồng Âm, bảo mật vé bằng chữ ký số P-256.',
        location: 'Cung Điền kinh Mỹ Đình, Hà Nội',
        startTime: '2026-12-19 19:30:00',
        endTime: '2026-12-19 23:00:00',
        totalTickets: 600,
        availableTickets: 512,
        ticketPrice: 2000.00
      },
      {
        title: 'VCT Pacific Stage 2 Finals - Valorant Esports',
        description: '[ESPORTS] Chung kết giải đấu Thể thao Điện tử Valorant khu vực Châu Á - Thái Bình Dương với vé điện tử chống bot.',
        location: 'Nhà thi đấu Phú Thọ, Quận 11, TP.HCM',
        startTime: '2026-12-22 14:00:00',
        endTime: '2026-12-22 21:00:00',
        totalTickets: 750,
        availableTickets: 590,
        ticketPrice: 2000.00
      },
      {
        title: 'Google I/O Extended Hanoi & Cloud Security Day',
        description: '[CÔNG NGHỆ] Ngày hội lập trình viên Android, AI, Cloud Native & Bảo mật API Zero-Trust năm 2026.',
        location: 'Keangnam Landmark 72, Phạm Hùng, Hà Nội',
        startTime: '2026-12-15 08:30:00',
        endTime: '2026-12-15 17:30:00',
        totalTickets: 400,
        availableTickets: 338,
        ticketPrice: 2000.00
      },
      {
        title: 'GENfest 2026 - Multi-Sensory Music Festival',
        description: '[LỄ HỘI] Lễ hội âm nhạc đa giác quan quy tụ dàn nghệ sĩ Quốc tế và Indie Việt Nam, thanh toán Napas 247 tự động.',
        location: 'The Global City, TP. Thủ Đức, TP.HCM',
        startTime: '2026-12-26 16:00:00',
        endTime: '2026-12-26 23:59:00',
        totalTickets: 1200,
        availableTickets: 920,
        ticketPrice: 2000.00
      },
      {
        title: 'Mỹ Tâm Live Concert: Tri Âm & Ánh Sáng',
        description: '[LIVE SHOW] Đêm nhạc cảm xúc với công nghệ ánh sáng Laser & vé điện tử chống chụp màn hình FLAG_SECURE.',
        location: 'SVĐ Hàng Đẫy, Đống Đa, Hà Nội',
        startTime: '2026-12-29 19:30:00',
        endTime: '2026-12-29 23:00:00',
        totalTickets: 900,
        availableTickets: 740,
        ticketPrice: 2000.00
      }
    ];

    const eventMapByTitle = new Map();

    for (const ev of realisticEvents) {
      const [existingEv] = await pool.execute('SELECT id FROM events WHERE title = ?', [ev.title]);
      if (existingEv.length > 0) {
        const evId = existingEv[0].id;
        await pool.execute(
          `UPDATE events
           SET description = ?, location = ?, start_time = ?, end_time = ?,
               ticket_price = ?, status = 'UPCOMING',
               available_tickets = CASE WHEN available_tickets < 50 THEN ? ELSE available_tickets END
           WHERE id = ?`,
          [ev.description, ev.location, ev.startTime, ev.endTime, ev.ticketPrice, ev.availableTickets, evId]
        );
        eventMapByTitle.set(ev.title, evId);
      } else {
        const evId = uuidv4();
        await pool.execute(
          `INSERT INTO events (id, title, description, location, start_time, end_time, organizer_id, total_tickets, available_tickets, ticket_price, status)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'UPCOMING')`,
          [evId, ev.title, ev.description, ev.location, ev.startTime, ev.endTime, actualOrgId, ev.totalTickets, ev.availableTickets, ev.ticketPrice]
        );
        eventMapByTitle.set(ev.title, evId);
      }
    }

    console.log(`[Seed] Upserted ${eventMapByTitle.size} realistic events priced at 2,000 VND.`);

    // 3. Làm sạch các vé trùng tên cũ của customer@secureticket.com và tạo bộ vé đa dạng, ký số thật 100%
    if (customer) {
      // Xóa bớt các vé cũ bị trùng lặp để ví vé hiển thị đa dạng, chuyên nghiệp
      await pool.execute('DELETE FROM tickets WHERE current_owner_id = ?', [customer.id]);

      const curatedTickets = [
        {
          eventTitle: 'Anh Trai Vượt Ngàn Chông Gai Concert - D-5',
          ticketCode: 'VIP-A12-8F91',
          status: 'ACTIVE',
          offsetMinutesAgo: 5
        },
        {
          eventTitle: 'Anh Trai "Say Hi" Live Concert - Đêm Vũ Đạo Số',
          ticketCode: 'FANZONE-08-3C4A',
          status: 'ACTIVE',
          offsetMinutesAgo: 25
        },
        {
          eventTitle: 'Hội nghị An toàn & Bảo mật Phần mềm KMA 2026',
          ticketCode: 'KMA-VIP01-9E2B',
          status: 'ACTIVE',
          offsetMinutesAgo: 60
        },
        {
          eventTitle: 'Hà Anh Tuấn Live Concert: Chân Trời Rực Rỡ',
          ticketCode: 'PLATINUM-R05-7D1F',
          status: 'ACTIVE',
          offsetMinutesAgo: 120
        },
        {
          eventTitle: 'SecureFest 2026 - Cyber Security Summit & Concert',
          ticketCode: 'SEC-STAGE-4A8C',
          status: 'ACTIVE',
          offsetMinutesAgo: 240
        },
        {
          eventTitle: 'Đen Vâu - Show Của Đen: Đồng Âm Hội Ngộ',
          ticketCode: 'DONGAM-GA19-6B5D',
          status: 'ACTIVE',
          offsetMinutesAgo: 360
        },
        {
          eventTitle: 'VCT Pacific Stage 2 Finals - Valorant Esports',
          ticketCode: 'ARENA-S14-2F7E',
          status: 'USED',
          offsetMinutesAgo: 1440
        },
        {
          eventTitle: 'Google I/O Extended Hanoi & Cloud Security Day',
          ticketCode: 'DEVSEC-P09-5C3A',
          status: 'USED',
          offsetMinutesAgo: 2880
        }
      ];

      for (const item of curatedTickets) {
        const evId = eventMapByTitle.get(item.eventTitle);
        if (!evId) continue;

        const ticketId = uuidv4();
        const nonce = generateSecureNonce(16);
        const qrSecretSeed = generateSecureNonce(32);
        const issuedTimestamp = Math.floor(Date.now() / 1000) - item.offsetMinutesAgo * 60;
        const issuedAt = new Date(issuedTimestamp * 1000);
        const usedAt = item.status === 'USED' ? new Date((issuedTimestamp + 1800) * 1000) : null;

        const canonical = createTicketCanonicalString(ticketId, evId, customer.id, nonce, issuedTimestamp);
        const serverSignature = signPayload(canonical);

        await pool.execute(
          `INSERT INTO tickets (
            id, ticket_code, event_id, current_owner_id, original_owner_id, status,
            device_bound_public_key, server_signature, nonce, qr_secret_seed, issued_at, used_at, issued_timestamp
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          [
            ticketId,
            item.ticketCode,
            evId,
            customer.id,
            customer.id,
            item.status,
            customer.device_public_key || null,
            serverSignature,
            nonce,
            qrSecretSeed,
            issuedAt,
            usedAt,
            issuedTimestamp
          ]
        );
      }

      console.log(`[Seed] Created ${curatedTickets.length} diverse, cryptographically signed tickets for customer@secureticket.com.`);
    }

    console.log('[Seed] Done! Sample accounts:');
    console.log('   - Customer:  customer@secureticket.com / SecureTicket@2026 (Nguyễn Minh Anh)');
    console.log('   - Friend 1:  friend@example.com / SecureTicket@2026 (Trần Hoàng Nam)');
    console.log('   - Friend 2:  linh.pham@secureticket.com / SecureTicket@2026 (Phạm Khánh Linh)');
    console.log('   - Friend 3:  minhkhoa.le@gmail.com / SecureTicket@2026 (Lê Minh Khoa)');
    console.log('   - Staff:     staff@secureticket.com / SecureTicket@2026');
    console.log('   - Organizer: organizer@secureticket.com / SecureTicket@2026');
    process.exit(0);
  } catch (err) {
    console.error('[Seed] Error:', err);
    process.exit(1);
  }
}

seedData();
