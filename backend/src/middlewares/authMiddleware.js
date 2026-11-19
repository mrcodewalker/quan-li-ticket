import jwt from 'jsonwebtoken';
import dotenv from 'dotenv';

dotenv.config();

export function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({
      success: false,
      error: 'Access denied. No authentication token provided.'
    });
  }

  const secret = process.env.JWT_SECRET;
  if (!secret) {
    return res.status(500).json({
      success: false,
      error: 'Server JWT configuration missing.'
    });
  }

  jwt.verify(token, secret, (err, user) => {
    if (err) {
      // Trả về 401 để OkHttp TokenAuthenticator tự động gọi /api/auth/refresh xoay vòng token
      return res.status(401).json({
        success: false,
        error: 'Invalid or expired access token.'
      });
    }
    req.user = user;
    next();
  });
}

export function authorizeRoles(...roles) {
  return (req, res, next) => {
    if (!req.user || !roles.includes(req.user.role)) {
      return res.status(403).json({
        success: false,
        error: `Forbidden. Role '${req.user?.role || 'anonymous'}' is not authorized to access this resource.`
      });
    }
    next();
  };
}

export function getClientInfo(req) {
  const ip = req.headers['x-forwarded-for'] || req.socket?.remoteAddress || '127.0.0.1';
  const userAgent = req.headers['user-agent'] || 'Unknown';
  return { ip, userAgent };
}
