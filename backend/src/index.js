import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import { testDbConnection } from './config/db.js';
import { initializeKeys } from './services/cryptoService.js';
import authRoutes from './routes/authRoutes.js';
import eventRoutes from './routes/eventRoutes.js';
import ticketRoutes from './routes/ticketRoutes.js';
import validationRoutes from './routes/validationRoutes.js';
import cryptoRoutes from './routes/cryptoRoutes.js';
import queueRoutes from './routes/queueRoutes.js';
import paymentRoutes from './routes/paymentRoutes.js';

dotenv.config();

const app = express();
const PORT = process.env.PORT || 5000;

// Middleware
app.use(cors({
  origin: '*', // Trong môi trường production chỉ định rõ domain hoặc Android app package
  methods: ['GET', 'POST', 'PUT', 'DELETE'],
  allowedHeaders: ['Content-Type', 'Authorization']
}));

app.use(express.json({
  limit: '1mb',
  verify: (req, res, buf) => {
    req.rawBody = buf.toString();
  }
}));
app.use(express.urlencoded({ extended: true }));

// Security Header cơ bản
app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('X-XSS-Protection', '1; mode=block');
  next();
});

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
      antiReplay: true
    }
  });
});

// 404 Handler
app.use((req, res) => {
  res.status(404).json({ success: false, error: 'Endpoint not found' });
});

// Global Error Handler
app.use((err, req, res, next) => {
  console.error('[Unhandled Error]', err);
  res.status(500).json({ success: false, error: 'Internal Server Error' });
});

// Start Server
async function startServer() {
  try {
    console.log('--- Starting SecureTicket Server ---');
    await testDbConnection();
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

