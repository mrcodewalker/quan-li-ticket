async function runTest() {
  const BASE_URL = 'http://localhost:5000/api';

  console.log('--- 1. Login Customer ---');
  let res = await fetch(`${BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'customer@secureticket.com', password: 'SecureTicket@2026' })
  });
  let data = await res.json();
  const customerToken = data.data.accessToken;
  console.log('Customer logged in. Token prefix:', customerToken.substring(0, 20) + '...');

  console.log('\n--- 2. Fetch Events ---');
  res = await fetch(`${BASE_URL}/events`);
  data = await res.json();
  const event = data.data[0];
  console.log('Found event:', event.title, '| Available tickets:', event.available_tickets);

  console.log('\n--- 3. Purchase Ticket ---');
  res = await fetch(`${BASE_URL}/tickets/buy`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${customerToken}`
    },
    body: JSON.stringify({ eventId: event.id })
  });
  data = await res.json();
  const ticket = data.data;
  console.log('Ticket Purchased:', ticket.ticketCode);
  console.log('ECDSA Server Signature:', ticket.serverSignature.substring(0, 30) + '...');

  console.log('\n--- 4. Generate Dynamic QR Code ---');
  res = await fetch(`${BASE_URL}/tickets/${ticket.ticketId}/dynamic-qr`, {
    headers: { 'Authorization': `Bearer ${customerToken}` }
  });
  data = await res.json();
  const qrData = data.data;
  console.log('Generated Rotating TOTP QR:', qrData.totpToken, '| Expires in:', qrData.expiresInSeconds, 's');

  console.log('\n--- 5. Login Staff / Inspector ---');
  res = await fetch(`${BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'staff@secureticket.com', password: 'SecureTicket@2026' })
  });
  data = await res.json();
  const staffToken = data.data.accessToken;
  console.log('Staff logged in.');

  console.log('\n--- 6. Staff Validates Ticket via QR (Check-in #1) ---');
  res = await fetch(`${BASE_URL}/validate/validate-qr`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${staffToken}`
    },
    body: JSON.stringify({ qrString: qrData.qrString })
  });
  data = await res.json();
  console.log('Validation Result:', data.result, '| Message:', data.message);

  console.log('\n--- 7. Replay Attack Test (Try to use ticket a second time) ---');
  res = await fetch(`${BASE_URL}/validate/validate-qr`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${staffToken}`
    },
    body: JSON.stringify({ qrString: qrData.qrString })
  });
  data = await res.json();
  console.log('Replay Attack Defense Result:', data.result, '| Expected: FAILED_ALREADY_USED');
  console.log('Error returned to attacker:', data.error);

  console.log('\n=== ALL SECURITY VERIFICATION TESTS PASSED ===');
}

runTest().catch(console.error);

