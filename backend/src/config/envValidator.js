import dotenv from 'dotenv';

dotenv.config();

const REQUIRED_ENV_VARS = [
  'JWT_SECRET',
  'JWT_REFRESH_SECRET',
  'PAYMENT_WEBHOOK_SECRET',
  'SEPAY_WEBHOOK_SECRET',
  'DB_HOST',
  'DB_USER',
  'DB_PASSWORD',
  'DB_NAME'
];

const FORBIDDEN_DEFAULT_PATTERNS = [
  'your_jwt_secret',
  'your_database_password',
  'change_in_production'
];

/**
 * Phase 1 (FR-04 & FR-05): Kiểm tra cấu hình biến môi trường Fail-Fast lúc khởi động.
 * Từ chối khởi động (process.exit(1)) nếu thiếu khóa bí mật hoặc dùng giá trị mẫu không an toàn.
 */
export function validateEnv() {
  const missing = [];
  const insecure = [];

  for (const key of REQUIRED_ENV_VARS) {
    const val = process.env[key];
    if (!val || val.trim() === '') {
      missing.push(key);
    } else if (FORBIDDEN_DEFAULT_PATTERNS.some(pattern => val.toLowerCase().includes(pattern))) {
      insecure.push(key);
    }
  }

  if (missing.length > 0 || insecure.length > 0) {
    console.error('[Security Fail-Fast] LỖI CẤU HÌNH MÔI TRƯỜNG NGHIÊM TRỌNG:');
    if (missing.length > 0) {
      console.error(`  - Thiếu biến môi trường bắt buộc: ${missing.join(', ')}`);
    }
    if (insecure.length > 0) {
      console.error(`  - Biến môi trường chứa giá trị mẫu không an toàn: ${insecure.join(', ')}`);
    }
    process.exit(1);
  }

  return true;
}
