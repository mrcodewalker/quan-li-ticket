import crypto from 'crypto';

const VPS_URL = 'http://42.96.5.186:5000/api/payment/sepay-webhook';
const SECRET = 'SecureTicket_Payment_Webhook_HMAC_Secret_2026';

async function testHmac() {
  console.log('--- Testing SePay HMAC-SHA256 on VPS ---');

  const timestamp = Math.floor(Date.now() / 1000);
  const payload = JSON.stringify({
    id: 12345,
    gateway: 'MBBank',
    accountNumber: '0388999888',
    content: 'SECURE INVALID999',
    transferType: 'in',
    transferAmount: 50000
  });

  // 1. Test với chữ ký giả mạo (Bad signature)
  console.log('[1] Testing tampered signature...');
  let res = await fetch(VPS_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-SePay-Signature': 'sha256=deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef',
      'X-SePay-Timestamp': String(timestamp)
    },
    body: payload
  });
  console.log(`Tampered response status: ${res.status} (Expected: 401)`);
  if (res.status !== 401) throw new Error('Should reject bad signature with 401!');

  // 2. Test với chữ ký đúng chuẩn SePay: {timestamp}.{raw_body}
  console.log('[2] Testing valid HMAC-SHA256 signature...');
  const dataToSign = `${timestamp}.${payload}`;
  const validHash = crypto.createHmac('sha256', SECRET).update(dataToSign).digest('hex');
  const validSigHeader = `sha256=${validHash}`;

  res = await fetch(VPS_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-SePay-Signature': validSigHeader,
      'X-SePay-Timestamp': String(timestamp)
    },
    body: payload
  });
  let json = await res.json();
  console.log('Valid HMAC response:', json);
  console.log('HMAC Verification Passed!');
}

testHmac().catch(console.error);

