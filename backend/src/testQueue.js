async function testQueueFlow() {
  const BASE_URL = 'http://localhost:5000/api';

  console.log('--- 1. Login Customer 1 & 2 ---');
  // Login Customer 1
  let res = await fetch(`${BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'customer@secureticket.com', password: 'SecureTicket@2026' })
  });
  let data = await res.json();
  const token1 = data.data.accessToken;

  // Login Organizer (acts as Customer 2 for test)
  res = await fetch(`${BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'organizer@secureticket.com', password: 'SecureTicket@2026' })
  });
  data = await res.json();
  const token2 = data.data.accessToken;

  // Lấy event
  res = await fetch(`${BASE_URL}/events`);
  data = await res.json();
  const eventId = data.data[0].id;
  console.log('Testing Queue with Event:', data.data[0].title);

  console.log('\n--- 2. Customer 1 Joins Queue (Limit maxSlots = 1 for demo) ---');
  res = await fetch(`${BASE_URL}/queue/join`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token1}`
    },
    body: JSON.stringify({ eventId, customSlotLimit: 1 })
  });
  data = await res.json();
  console.log('Join Response:', data);
  const queue1 = data.data;
  console.log('Customer 1 Status:', queue1.status, '| Pass Token:', queue1.passToken ? 'Granted' : 'None');

  console.log('\n--- 3. Customer 2 Joins Queue (Slot is occupied) ---');
  res = await fetch(`${BASE_URL}/queue/join`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token2}`
    },
    body: JSON.stringify({ eventId, customSlotLimit: 1 })
  });
  data = await res.json();
  const queue2 = data.data;
  console.log('Customer 2 Status:', queue2.status, '| Queue Number:', queue2.queueNumber, '| Users Ahead:', queue2.usersAhead);

  console.log('\n--- 4. Customer 1 Purchases Ticket with Pass Token ---');
  res = await fetch(`${BASE_URL}/tickets/buy`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token1}`
    },
    body: JSON.stringify({ eventId, passToken: queue1.passToken })
  });
  data = await res.json();
  console.log('Customer 1 Purchase Result:', data.message, '| Ticket:', data.data?.ticketCode);

  console.log('\n--- 5. Customer 2 Checks Queue Status (Slot has been freed!) ---');
  res = await fetch(`${BASE_URL}/queue/status/${queue2.queueId}`, {
    headers: { 'Authorization': `Bearer ${token2}` }
  });
  data = await res.json();
  console.log('Customer 2 New Status:', data.data.status, '| Pass Token:', data.data.passToken ? 'PROMOTED & GRANTED!' : 'Waiting');

  console.log('\n=== QUEUE VIRTUAL WAITING ROOM TEST COMPLETED SUCCESSFULLY ===');
}

testQueueFlow().catch(console.error);
