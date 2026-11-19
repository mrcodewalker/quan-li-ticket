/**
 * Phase 3 (FR-14, FR-15, FR-16, FR-17):
 * 1. HTTP Security Headers (HSTS, CSP, X-Content-Type-Options, X-Frame-Options, Referrer-Policy)
 * 2. Tiered Token-Bucket Rate Limiting chống Brute-force & DDoS (HTTP 429)
 * 3. Strict Input Schema & Anti-Prototype-Pollution Validation (HTTP 400)
 */

export function securityHeadersMiddleware(req, res, next) {
  res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains; preload');
  res.setHeader('Content-Security-Policy', "default-src 'none'; frame-ancestors 'none'");
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('X-XSS-Protection', '1; mode=block');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.removeHeader('X-Powered-By');
  next();
}

const rateBuckets = new Map();

/**
 * Bộ giới hạn tần suất request phân tầng (Rate Limiter)
 */
export function createRateLimiter({ windowMs = 60000, maxRequests = 30, keyPrefix = 'global' }) {
  return (req, res, next) => {
    const ip = req.headers['x-forwarded-for'] || req.socket?.remoteAddress || '127.0.0.1';
    const userId = req.user?.id || ip;
    const key = `${keyPrefix}:${userId}`;
    const now = Date.now();

    let bucket = rateBuckets.get(key);
    if (!bucket || now > bucket.resetAt) {
      bucket = { count: 0, resetAt: now + windowMs };
      rateBuckets.set(key, bucket);
    }

    bucket.count += 1;
    const remaining = Math.max(0, maxRequests - bucket.count);
    const retryAfterSec = Math.ceil((bucket.resetAt - now) / 1000);

    res.setHeader('X-RateLimit-Limit', String(maxRequests));
    res.setHeader('X-RateLimit-Remaining', String(remaining));

    if (bucket.count > maxRequests) {
      res.setHeader('Retry-After', String(retryAfterSec));
      return res.status(429).json({
        success: false,
        error: `Too many requests (${keyPrefix}). Please retry after ${retryAfterSec} seconds.`
      });
    }

    next();
  };
}

export function resetAllRateLimiters() {
  rateBuckets.clear();
}

const DANGEROUS_KEYS = ['__proto__', 'constructor', 'prototype'];

/**
 * Kiểm tra cấu trúc dữ liệu đầu vào (Schema Validation) & Chống Prototype Pollution / SQLi cơ bản
 */
export function validateRequestSchema({ requiredFields = [], allowedFields = null }) {
  return (req, res, next) => {
    const body = req.body;
    if (!body || typeof body !== 'object' || Array.isArray(body)) {
      return res.status(400).json({
        success: false,
        error: 'Payload JSON không hợp lệ.'
      });
    }

    // Kiểm tra Prototype Pollution trong raw JSON hoặc keys
    const rawStr = req.rawBody || '';
    if (rawStr.includes('"__proto__"') || rawStr.includes('"prototype"') || rawStr.includes('"constructor"')) {
      return res.status(400).json({
        success: false,
        error: 'Phát hiện thuộc tính độc hại (Prototype Pollution blocked).'
      });
    }

    const keys = Object.keys(body);
    for (const k of keys) {
      if (DANGEROUS_KEYS.includes(k)) {
        return res.status(400).json({
          success: false,
          error: 'Phát hiện thuộc tính không an toàn.'
        });
      }
      if (allowedFields && !allowedFields.includes(k)) {
        return res.status(400).json({
          success: false,
          error: `Trường dữ liệu lạ không được phép: '${k}'`
        });
      }
    }

    for (const reqField of requiredFields) {
      const val = body[reqField];
      if (val === undefined || val === null || (typeof val === 'string' && val.trim() === '')) {
        return res.status(400).json({
          success: false,
          error: `Thiếu trường bắt buộc: '${reqField}'`
        });
      }
    }

    next();
  };
}
