import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { pool } from '../config/db.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const KEYS_DIR = path.resolve(__dirname, '../../keys');

let serverPrivateKeyPem = null;
let serverPublicKeyPem = null;

export function ensureKeysLoaded() {
  if (serverPrivateKeyPem && serverPublicKeyPem) {
    return;
  }
  if (!fs.existsSync(KEYS_DIR)) {
    fs.mkdirSync(KEYS_DIR, { recursive: true });
  }

  const privPath = path.join(KEYS_DIR, 'server_private.pem');
  const pubPath = path.join(KEYS_DIR, 'server_public.pem');

  if (fs.existsSync(privPath) && fs.existsSync(pubPath)) {
    serverPrivateKeyPem = fs.readFileSync(privPath, 'utf8');
    serverPublicKeyPem = fs.readFileSync(pubPath, 'utf8');
  } else {
    const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', {
      namedCurve: 'prime256v1',
      publicKeyEncoding: {
        type: 'spki',
        format: 'pem'
      },
      privateKeyEncoding: {
        type: 'pkcs8',
        format: 'pem'
      }
    });

    serverPrivateKeyPem = privateKey;
    serverPublicKeyPem = publicKey;

    fs.writeFileSync(privPath, privateKey, { mode: 0o600 });
    fs.writeFileSync(pubPath, publicKey);
  }
}

/**
 * Khởi tạo hoặc nạp cặp khóa ECDSA (P-256) của Server
 */
export async function initializeKeys() {
  ensureKeysLoaded();
  console.log('[Crypto] Server ECDSA P-256 keypair initialized.');

  // Cập nhật hoặc lưu vào bảng crypto_keys trên MySQL
  try {
    const keyId = 'server-ecdsa-v1';
    await pool.execute(
      `INSERT INTO crypto_keys (key_id, algorithm, public_key_pem, is_active)
       VALUES (?, 'ES256', ?, TRUE)
       ON DUPLICATE KEY UPDATE public_key_pem = VALUES(public_key_pem), is_active = TRUE`,
      [keyId, serverPublicKeyPem]
    );
    console.log('[Crypto] Server public key registered in MySQL (crypto_keys).');
  } catch (err) {
    console.error('[Crypto] Error registering public key in DB:', err.message);
  }
}

export function getServerPublicKey() {
  ensureKeysLoaded();
  return serverPublicKeyPem;
}

/**
 * Ký dữ liệu vé bằng ECDSA P-256 + SHA-256 (Server Private Key)
 */
export function signPayload(dataString) {
  ensureKeysLoaded();
  const sign = crypto.createSign('SHA256');
  sign.update(dataString);
  sign.end();
  return sign.sign(serverPrivateKeyPem, 'base64');
}

/**
 * Xác minh chữ ký số của Server bằng Server Public Key
 */
export function verifyServerSignature(dataString, signatureBase64) {
  ensureKeysLoaded();
  try {
    const verify = crypto.createVerify('SHA256');
    verify.update(dataString);
    verify.end();
    return verify.verify(serverPublicKeyPem, signatureBase64, 'base64');
  } catch (e) {
    console.error('[Crypto] verifyServerSignature error:', e);
    return false;
  }
}

/**
 * Sinh chuỗi vé chuẩn hóa để ký và xác minh
 * Định dạng: TICKET_ID|EVENT_ID|OWNER_ID|NONCE|TIMESTAMP_SECONDS
 */
export function createTicketCanonicalString(ticketId, eventId, ownerId, nonce, issuedAt) {
  const tsInSeconds = typeof issuedAt === 'number'
    ? issuedAt
    : Math.floor(new Date(issuedAt).getTime() / 1000);
  return `${ticketId}|${eventId}|${ownerId}|${nonce}|${tsInSeconds}`;
}

/**
 * Sinh mã QR động (TOTP-like HMAC SHA-256 quay vòng mỗi 30s)
 */
export function generateDynamicQrPayload(ticketId, qrSecretSeed) {
  const timeSlice = Math.floor(Date.now() / 30000);
  const hmac = crypto.createHmac('sha256', qrSecretSeed)
    .update(`${ticketId}:${timeSlice}`)
    .digest('hex')
    .substring(0, 16);

  // Trả về payload mã hóa dạng JSON hoặc chuỗi kết hợp
  return {
    ticketId,
    timeSlice,
    totpToken: hmac,
    expiresInSeconds: 30 - (Math.floor(Date.now() / 1000) % 30)
  };
}

/**
 * Xác thực mã QR động, chấp nhận lệch 1 time window (±30s) để tránh clock drift
 */
export function verifyDynamicQrPayload(ticketId, clientTimeSlice, clientToken, qrSecretSeed) {
  const currentTimeSlice = Math.floor(Date.now() / 30000);

  // Kiểm tra thời gian trong khoảng [currentTimeSlice - 1, currentTimeSlice + 1]
  if (Math.abs(currentTimeSlice - clientTimeSlice) > 1) {
    return { valid: false, reason: 'QR Code expired or clock mismatch' };
  }

  const expectedHmac = crypto.createHmac('sha256', qrSecretSeed)
    .update(`${ticketId}:${clientTimeSlice}`)
    .digest('hex')
    .substring(0, 16);

  const bufClient = Buffer.from(clientToken, 'utf8');
  const bufExpected = Buffer.from(expectedHmac, 'utf8');

  if (bufClient.length !== bufExpected.length || !crypto.timingSafeEqual(bufClient, bufExpected)) {
    return { valid: false, reason: 'Invalid TOTP QR Token' };
  }

  return { valid: true };
}

/**
 * Xác minh chữ ký số thiết bị (Device Binding)
 * Kiểm tra xem thiết bị có đúng là thiết bị sở hữu giữ Private Key trong Keystore không.
 */
export function verifyDeviceSignature(challenge, signatureBase64, devicePublicKeyPem) {
  try {
    const verify = crypto.createVerify('SHA256');
    verify.update(challenge);
    verify.end();
    return verify.verify(devicePublicKeyPem, signatureBase64, 'base64');
  } catch (err) {
    console.error('[Crypto] Device signature verification error:', err.message);
    return false;
  }
}

/**
 * Sinh Nonce ngẫu nhiên bảo mật cao (chống Replay)
 */
export function generateSecureNonce(bytes = 32) {
  return crypto.randomBytes(bytes).toString('hex');
}
