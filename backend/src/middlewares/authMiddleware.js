import jwt from 'jsonwebtoken';

export function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({
      success: false,
      error: 'Access denied. No authentication token provided.'
    });
  }

  jwt.verify(token, process.env.JWT_SECRET || 'SecureTicket2026_TopSecretJwtKey!#998244353', (err, user) => {
    if (err) {
      return res.status(403).json({
        success: false,
        error: 'Invalid or expired token.'
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
  const ip = req.headers['x-forwarded-for'] || req.socket?.remoteAddress || null;
  const userAgent = req.headers['user-agent'] || null;
  return { ip, userAgent };
}

