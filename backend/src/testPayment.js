const API_BASE = 'http://localhost:5000/api';

async function runPaymentTest() {
  console.log('=== TEST PAYMENT FLOW (VIETQR & WEBHOOK SIGNATURE) ===');

  try {
    // 1. Đăng nhập Customer
    console.log('[1] Logging in customer...');
    let res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: 'customer@secureticket.com',
        password: 'SecureTicket@2026'
      })
    });
    let json = await res.json();
    const token = json.data.accessToken;
    console.log('Customer logged in successfully.');

    // 2. Lấy danh sách sự kiện
    console.log('[2] Fetching events...');
    res = await fetch(`${API_BASE}/events`);
    json = await res.json();
    const event = json.data[0];
    console.log(`Event selected: "${event.title}", Price: ${event.ticket_price} VND`);

    // 3. Tạo đơn hàng thanh toán VietQR
    console.log('[3] Creating VietQR payment order...');
    res = await fetch(`${API_BASE}/payment/create-vietqr`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ eventId: event.id })
    });
    json = await res.json();
    const orderData = json.data;
    console.log('Order created:');
    console.log(` - Order ID: ${orderData.orderId}`);
    console.log(` - Order Code: ${orderData.orderCode}`);
    console.log(` - Amount: ${orderData.amount} VND`);
    console.log(` - Bank: ${orderData.bankId} (${orderData.bankAccountNo})`);
    console.log(` - Transfer Memo: ${orderData.transferContent}`);
    console.log(` - VietQR Image URL: ${orderData.qrImageUrl}`);

    // 4. Kiểm tra trạng thái đơn hàng (PENDING)
    console.log('[4] Checking order status...');
    res = await fetch(`${API_BASE}/payment/order/${orderData.orderId}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    json = await res.json();
    console.log(`Current status: ${json.data.status}`);
    if (json.data.status !== 'PENDING') {
      throw new Error('Order should be PENDING!');
    }

    // 5. Kiểm thử bảo mật: Giả mạo Webhook với chữ ký sai (HMAC Tampering Test)
    console.log('[5] Testing Security: Webhook with forged/tampered signature...');
    res = await fetch(`${API_BASE}/payment/webhook`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        code: '00',
        desc: 'success',
        data: {
          orderCode: orderData.orderCode,
          amount: orderData.amount
        },
        signature: 'deadbeef1234567890abcdefdeadbeef1234567890abcdefdeadbeef12345678'
      })
    });
    if (res.status === 403) {
      console.log('Tampered webhook successfully rejected with 403 Forbidden! Security verification works.');
    } else {
      throw new Error(`CRITICAL BUG: Tampered webhook returned ${res.status} instead of 403!`);
    }

    // 6. Giả lập thanh toán thành công (Simulate sandbox transfer)
    console.log('[6] Simulating successful payment transfer in sandbox...');
    res = await fetch(`${API_BASE}/payment/simulate-transfer`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ orderId: orderData.orderId })
    });
    json = await res.json();
    console.log('Simulation response:', json.message);
    console.log('Issued Ticket ID:', json.data.ticketId);
    console.log('Ticket Code:', json.data.ticketCode);
    console.log('Server Signature (ECDSA P-256):', json.data.serverSignature.substring(0, 40) + '...');

    // 7. Kiểm tra lại trạng thái đơn hàng (PAID)
    console.log('[7] Verifying updated order status...');
    res = await fetch(`${API_BASE}/payment/order/${orderData.orderId}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    json = await res.json();
    console.log(`Updated status: ${json.data.status}`);
    if (json.data.status !== 'PAID') {
      throw new Error('Order should be PAID now!');
    }
    console.log('Attached ticket code:', json.data.ticket.ticket_code);

    // 8. Thử sinh Dynamic TOTP QR của vé mới được mua
    console.log('[8] Generating Dynamic TOTP QR for the new ticket...');
    res = await fetch(`${API_BASE}/tickets/${json.data.ticket.id}/dynamic-qr`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    json = await res.json();
    console.log('Dynamic QR generated:', json.data.qrString);
    console.log('TOTP Token (30s window):', json.data.totpToken);

    console.log('\n=== ALL PAYMENT & SECURITY WEBHOOK TESTS PASSED 100%! ===');
    process.exit(0);
  } catch (error) {
    console.error('Payment test failed:', error.message);
    process.exit(1);
  }
}

runPaymentTest();
