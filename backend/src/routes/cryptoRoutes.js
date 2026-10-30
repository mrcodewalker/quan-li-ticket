import express from 'express';
import { getServerPublicKey } from '../services/cryptoService.js';

const router = express.Router();

/**
 * Trả về Khóa công khai của Server (ECDSA P-256)
 * Android app sử dụng khóa này để kiểm tra tính toàn vẹn và xác thực vé
 */
router.get('/server-public-key', (req, res) => {
  const pem = getServerPublicKey();
  if (!pem) {
    return res.status(503).json({ success: false, error: 'Server keypair not ready.' });
  }

  // Chuyển đổi PEM thành clean Base64 (bỏ header/footer) để app Android dễ import vào X509EncodedKeySpec
  const rawBase64 = pem
    .replace('-----BEGIN PUBLIC KEY-----', '')
    .replace('-----END PUBLIC KEY-----', '')
    .replace(/\s+/g, '');

  res.json({
    success: true,
    data: {
      algorithm: 'ECDSA',
      curve: 'secp256r1 (P-256)',
      hash: 'SHA-256',
      publicKeyPem: pem,
      publicKeyBase64: rawBase64
    }
  });
});

export default router;

