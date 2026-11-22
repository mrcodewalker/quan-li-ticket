export const MAX_ACTIVE_TICKETS_PER_EVENT = 6;

export const SEAT_NUMBERS = Array.from({ length: 40 }, (_, i) => String(10 + i));

export const ZONE_TIERS = [
  {
    code: 'STAGE-V',
    name: 'Stage View 360°',
    multiplier: 1.5,
    perks: 'Sát sân khấu trung tâm · Lối đi ưu tiên · Check-in TEE'
  },
  {
    code: 'VIP-A',
    name: 'VIP Khu A',
    multiplier: 1.4,
    perks: 'Góc nhìn chính diện · Khu vực ngồi riêng · Quà tặng sự kiện'
  },
  {
    code: 'VIP-B',
    name: 'VIP Khu B',
    multiplier: 1.3,
    perks: 'Cánh phải sân khấu · Ghế đánh số cố định'
  },
  {
    code: 'PLATINUM',
    name: 'Platinum Lounge',
    multiplier: 1.2,
    perks: 'Khán đài nâng cao · Tầm nhìn toàn cảnh'
  },
  {
    code: 'FANZONE',
    name: 'Fanzone Sôi động',
    multiplier: 1.1,
    perks: 'Khu vực tương tác gần sân khấu'
  },
  {
    code: 'PREM-S',
    name: 'Standard Tiêu chuẩn',
    multiplier: 1.0,
    perks: 'Vé tiêu chuẩn · Đầy đủ bảo mật ECDSA & TOTP 30s'
  }
];

export function getZoneTier(zoneCode) {
  const normalized = String(zoneCode || '').trim().toUpperCase();
  return ZONE_TIERS.find(t => t.code === normalized) || ZONE_TIERS[ZONE_TIERS.length - 1];
}

export function computeTierPrice(basePrice, multiplier) {
  const raw = Number(basePrice || 0) * Number(multiplier || 1);
  return Math.round(raw / 100) * 100;
}

/**
 * Tách zone và seatNumber từ ticket_code dạng "VIP-A12-8F91" hoặc "STAGE-V10-ABCD"
 */
export function parseTicketSeat(ticketCode) {
  if (!ticketCode) return { seatZone: null, seatNumber: null };
  const upper = String(ticketCode).toUpperCase();
  for (const tier of ZONE_TIERS) {
    if (upper.startsWith(tier.code)) {
      const rest = upper.slice(tier.code.length);
      const match = rest.match(/^(\d{2})-/);
      if (match) {
        return { seatZone: tier.code, seatNumber: match[1] };
      }
    }
  }
  return { seatZone: null, seatNumber: null };
}
