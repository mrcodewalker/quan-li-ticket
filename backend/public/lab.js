const consoleOut = document.getElementById('consoleOut');
const sessionBadge = document.getElementById('sessionBadge');

let accessToken = '';
let refreshToken = '';

function log(title, payload) {
  const ts = new Date().toLocaleTimeString();
  const body = typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2);
  consoleOut.textContent = `[${ts}] ${title}\n${body}\n\n` + consoleOut.textContent;
}

async function apiFetch(path, options = {}, tokenOverride = null) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  const tok = tokenOverride !== null ? tokenOverride : accessToken;
  if (tok) headers.Authorization = `Bearer ${tok}`;
  const res = await fetch(path, { ...options, headers });
  const text = await res.text();
  let json;
  try { json = JSON.parse(text); } catch { json = { raw: text }; }
  return { status: res.status, data: json };
}

document.getElementById('btnClearLog').addEventListener('click', () => {
  consoleOut.textContent = '';
});

document.getElementById('btnLogin').addEventListener('click', async () => {
  const email = document.getElementById('emailInput').value.trim();
  const password = document.getElementById('passwordInput').value;
  const r = await apiFetch('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password })
  }, '');
  if (r.status === 200 && r.data?.data) {
    accessToken = r.data.data.accessToken;
    refreshToken = r.data.data.refreshToken;
    sessionBadge.textContent = `${r.data.data.user.email} (${r.data.data.user.role})`;
  }
  log(`LOGIN HTTP ${r.status}`, r.data);
});

document.getElementById('btnLoadKey').addEventListener('click', async () => {
  const r = await apiFetch('/api/crypto/server-public-key', { method: 'GET' }, '');
  log(`SERVER ECDSA P-256 PUBLIC KEY (HTTP ${r.status})`, r.data);
});

document.getElementById('btnMyOrders').addEventListener('click', async () => {
  const r = await apiFetch('/api/payment/my-orders', { method: 'GET' });
  const pending = (r.data?.data || []).find(o => o.status === 'PENDING');
  if (pending) document.getElementById('orderCodeInput').value = pending.orderCode;
  log(`MY ORDERS (HTTP ${r.status})`, r.data);
});

async function runWebhookSim(attackMode) {
  const orderCode = document.getElementById('orderCodeInput').value.trim();
  if (!orderCode) return log('WEBHOOK SIM', 'Vui lòng nhập Order Code (hoặc bấm Tải đơn PENDING).');
  const r = await apiFetch('/api/payment/simulate-signed-webhook', {
    method: 'POST',
    body: JSON.stringify({ orderCode, attackMode })
  });
  log(`SEPAY WEBHOOK [${attackMode}] -> HTTP ${r.status}`, r.data);
}

document.getElementById('btnValidWebhook').addEventListener('click', () => runWebhookSim('VALID'));
document.getElementById('btnInvalidWebhook').addEventListener('click', () => runWebhookSim('INVALID_HMAC'));

document.getElementById('btnRotateToken').addEventListener('click', async () => {
  if (!refreshToken) return log('ROTATE TOKEN', 'Hãy đăng nhập trước.');
  const r = await apiFetch('/api/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refreshToken })
  }, '');
  if (r.status === 200 && r.data?.data) {
    accessToken = r.data.data.accessToken;
    refreshToken = r.data.data.refreshToken;
  }
  log(`TOKEN ROTATION -> HTTP ${r.status}`, r.data);
});

document.getElementById('btnReuseAttack').addEventListener('click', async () => {
  const activeEmail = document.getElementById('emailInput').value.trim().toLowerCase();
  const probeEmail = activeEmail === 'friend@example.com' ? 'linh.pham@secureticket.com' : 'friend@example.com';
  const login = await apiFetch('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email: probeEmail, password: 'SecureTicket@2026' })
  }, '');
  if (login.status !== 200) return log('PROBE LOGIN FAILED', login.data);
  const rt1 = login.data.data.refreshToken;
  const rot = await apiFetch('/api/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken: rt1 }) }, '');
  const reuse = await apiFetch('/api/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken: rt1 }) }, '');
  log(`TOKEN REUSE DETECTOR (${probeEmail})`, {
    step1_rotate: rot.status,
    step2_replay_rt1_status: reuse.status,
    defense: reuse.data
  });
});

async function getFirstEventId() {
  const ev = await apiFetch('/api/events', { method: 'GET' }, '');
  return ev.data?.data?.[0]?.id || 'evt-1';
}

document.getElementById('btnAtk1').addEventListener('click', async () => {
  const eventId = await getFirstEventId();
  const r = await apiFetch('/api/payment/create-vietqr', {
    method: 'POST',
    body: JSON.stringify({ eventId })
  });
  log(`ATTACK 1 (Bot cURL without TEE) -> HTTP ${r.status}`, r.data);
});

document.getElementById('btnAtk2').addEventListener('click', async () => {
  const eventId = await getFirstEventId();
  const nonce = 'pc-lab-replay-' + Date.now();
  const ts = Math.floor(Date.now() / 1000);
  const payload = { eventId, clientNonce: nonce, timestamp: ts, deviceSignature: 'FORGED_OR_TEST_SIG', seatZone: 'PREM-S' };
  const r1 = await apiFetch('/api/payment/create-vietqr', { method: 'POST', body: JSON.stringify(payload) });
  const r2 = await apiFetch('/api/payment/create-vietqr', { method: 'POST', body: JSON.stringify(payload) });
  log(`ATTACK 2 (Replay Nonce) -> Req1 HTTP ${r1.status}, Req2 HTTP ${r2.status}`, r2.data);
});

document.getElementById('btnAtk3').addEventListener('click', () => runWebhookSim('INVALID_HMAC'));

document.getElementById('btnAtk4').addEventListener('click', async () => {
  const staffLogin = await apiFetch('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email: 'staff@secureticket.com', password: 'SecureTicket@2026' })
  }, '');
  const staffTok = staffLogin.data?.data?.accessToken || accessToken;
  const ts = Math.floor(Date.now() / 30000);
  const forgedQr = JSON.stringify({ tId: 'forged-ticket-id', ts, totp: '123456', sig: 'FORGED_ECDSA' });
  const r = await apiFetch('/api/validate/validate-qr', {
    method: 'POST',
    body: JSON.stringify({ qrString: forgedQr })
  }, staffTok);
  log(`ATTACK 4 (Forged QR at Gate) -> HTTP ${r.status}`, r.data);
});

document.getElementById('btnAtk5').addEventListener('click', async () => {
  const eventId = await getFirstEventId();
  const r = await apiFetch(`/api/events/${eventId}/booking-context`, { method: 'GET' });
  log(`ATTACK 5 (Anti-Scalping Quota Check) -> HTTP ${r.status}`, {
    userActiveTickets: r.data?.data?.userActiveTickets,
    userPendingOrders: r.data?.data?.userPendingOrders,
    userTotalActive: r.data?.data?.userTotalActive,
    maxTicketsPerEvent: r.data?.data?.maxTicketsPerEvent
  });
});

document.getElementById('btnAuditLogs').addEventListener('click', async () => {
  const orgLogin = await apiFetch('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email: 'organizer@secureticket.com', password: 'SecureTicket@2026' })
  }, '');
  const orgTok = orgLogin.data?.data?.accessToken || accessToken;
  const r = await apiFetch('/api/events/organizer/audit-logs', { method: 'GET' }, orgTok);
  log(`SIEM AUDIT LOGS -> HTTP ${r.status}`, (r.data?.data || []).slice(0, 10));
});
