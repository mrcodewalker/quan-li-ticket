const API_BASE = 'http://localhost:5000/api';

async function testSepayWebhook() {
  console.log('=== TEST SEPAY WEBHOOK INTEGRATION ===');

  // 1. Đăng nhập customer
  let res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'customer@secureticket.com', password: 'SecureTicket@2026' })
  });
  let json = await res.json();
  const token = json.data.accessToken;

  // 2. Lấy event
  res = await fetch(`${API_BASE}/events`);
  json = await res.json();
  const event = json.data[0];

  // 3. Tạo order VietQR
  res = await fetch(`${API_BASE}/payment/create-vietqr`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({ eventId: event.id })
  });
  json = await res.json();
  const order = json.data;
  console.log(`Order created: #${order.orderCode}, amount: ${order.amount}`);

  // 4. Giả lập SePay gửi Webhook đến /api/payment/sepay-webhook
  console.log('Sending mock SePay webhook payload...');
  const sepayMockPayload = {
    id: 998822,
    gateway: 'MBBank',
    transactionDate: '2026-10-30 16:05:00',
    accountNumber: '0388999888',
    subAccount: null,
    code: null,
    content: `SECURE ${order.orderCode} NGUYEN VAN A CHUYEN TIEN`,
    transferType: 'in',
    description: `SECURE ${order.orderCode}`,
    transferAmount: order.amount,
    referenceCode: 'FT99881122',
    accumulated: 15000000
  };

  res = await fetch(`${API_BASE}/payment/sepay-webhook`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(sepayMockPayload)
  });
  json = await res.json();
  console.log('SePay Webhook response:', json);

  if (json.success) {
    console.log('SUCCESS! Ticket issued via SePay Webhook:', json.data.ticketCode);
    console.log('ECDSA P-256 signature:', json.data.serverSignature.substring(0, 30) + '...');
  } else {
    throw new Error('SePay webhook failed: ' + JSON.stringify(json));
  }

  // 5. Kiểm tra trạng thái đơn hàng trong DB
  res = await fetch(`${API_BASE}/payment/order/${order.orderId}`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  json = await res.json();
  console.log('Order status now:', json.data.status);
  if (json.data.status !== 'PAID') {
    throw new Error('Order status should be PAID!');
  }

  console.log('=== SEPAY WEBHOOK INTEGRATION TEST PASSED 100%! ===');
}

testSepayWebhook().catch(console.error);

