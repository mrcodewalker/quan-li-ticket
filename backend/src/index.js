import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import { validateEnv } from './config/envValidator.js';
import { testDbConnection } from './config/db.js';
import { initializeKeys } from './services/cryptoService.js';
import { securityHeadersMiddleware } from './middlewares/securityMiddleware.js';
import authRoutes, { ensureRefreshTokenTable } from './routes/authRoutes.js';
import eventRoutes from './routes/eventRoutes.js';
import ticketRoutes from './routes/ticketRoutes.js';
import validationRoutes from './routes/validationRoutes.js';
import cryptoRoutes from './routes/cryptoRoutes.js';
import queueRoutes from './routes/queueRoutes.js';
import paymentRoutes from './routes/paymentRoutes.js';

dotenv.config();
validateEnv();

export const app = express();
const PORT = process.env.PORT || 5000;

// Phase 3 (FR-14): HTTP Security Headers (HSTS, CSP, nosniff, Frame DENY)
app.use(securityHeadersMiddleware);

// Phase 3 (FR-15): Strict CORS Policy
const allowedOrigins = process.env.ALLOWED_ORIGINS
  ? process.env.ALLOWED_ORIGINS.split(',').map(o => o.trim())
  : ['http://localhost:5000', 'http://10.0.2.2:5000', 'https://api.secureticket.vn'];

app.use(cors({
  origin: (origin, callback) => {
    // Cho phép Android Native App (không gửi Origin header) hoặc các Origin trong Whitelist
    if (!origin || allowedOrigins.includes(origin)) {
      callback(null, true);
    } else {
      callback(new Error('CORS policy blocked this origin'));
    }
  },
  methods: ['GET', 'POST', 'PUT', 'DELETE'],
  allowedHeaders: ['Content-Type', 'Authorization', 'X-Device-Signature', 'x-sepay-signature', 'x-sepay-timestamp']
}));

app.use(express.json({
  limit: '1mb',
  verify: (req, res, buf) => {
    req.rawBody = buf.toString();
  }
}));
app.use(express.urlencoded({ extended: true }));

// Routes
app.use('/api/auth', authRoutes);
app.use('/api/events', eventRoutes);
app.use('/api/tickets', ticketRoutes);
app.use('/api/validate', validationRoutes);
app.use('/api/crypto', cryptoRoutes);
app.use('/api/queue', queueRoutes);
app.use('/api/payment', paymentRoutes);

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({
    status: 'UP',
    timestamp: new Date().toISOString(),
    service: 'SecureTicket Backend API',
    security: {
      ecdsaP256: true,
      dynamicTOTP: true,
      deviceBinding: true,
      antiReplay: true,
      dualTokenRotation: true,
      customerPiiEncryption: 'AES-256-GCM',
      webhookFailClosed: true
    }
  });
});

// 404 Handler
app.use((req, res) => {
  res.status(404).json({ success: false, error: 'Endpoint not found' });
});

// Global Error Handler
app.use((err, req, res, next) => {
  if (err && err.message === 'CORS policy blocked this origin') {
    return res.status(403).json({ success: false, error: err.message });
  }
  console.error('[Unhandled Error]', err);
  res.status(500).json({ success: false, error: 'Internal Server Error' });
});

// Start Server when executed directly
if (process.env.NODE_ENV !== 'test') {
  async function startServer() {
    try {
      console.log('--- Starting SecureTicket Server (Hardened Phase 1-3) ---');
      await testDbConnection();
      await ensureRefreshTokenTable();
      await initializeKeys();

      app.listen(PORT, '0.0.0.0', () => {
        console.log(`[Server] SecureTicket Backend listening on http://0.0.0.0:${PORT}`);
        console.log(`[Server] Health check available at http://localhost:${PORT}/health`);
      });
    } catch (err) {
      console.error('[Server] Fatal startup error:', err);
      process.exit(1);
    }
  }

  startServer();
}
