async function testPublicWebhook() {
  const res = await fetch('http://42.96.5.186:5000/api/payment/sepay-webhook', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      id: 112233,
      transferType: 'in',
      content: 'SECURE TEST999',
      transferAmount: 10000
    })
  });
  const data = await res.json();
  console.log('Public Webhook Response from VPS:', data);
}

testPublicWebhook().catch(console.error);

