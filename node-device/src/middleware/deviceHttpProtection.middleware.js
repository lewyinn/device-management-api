const rateLimitStores = new Map();
const throttleStores = new Map();

const getClientIp = (req) => (
    req.ip ||
    req.socket?.remoteAddress ||
    'unknown'
);

const getDeviceOrIpKey = (req) => req.params.id || getClientIp(req);

const secondsUntil = (timestamp) => Math.max(1, Math.ceil((timestamp - Date.now()) / 1000));

const createRateLimiter = ({
    name,
    windowMs,
    maxRequests,
    keyGenerator = getDeviceOrIpKey
}) => {
    const store = new Map();
    rateLimitStores.set(name, store);

    return (req, res, next) => {
        const now = Date.now();
        const key = keyGenerator(req);
        const current = store.get(key);

        if (!current || now >= current.resetAt) {
            const resetAt = now + windowMs;
            store.set(key, { count: 1, resetAt });
            res.setHeader('X-RateLimit-Limit', String(maxRequests));
            res.setHeader('X-RateLimit-Remaining', String(maxRequests - 1));
            res.setHeader('X-RateLimit-Reset', String(Math.ceil(resetAt / 1000)));
            return next();
        }

        if (current.count >= maxRequests) {
            const retryAfter = secondsUntil(current.resetAt);
            res.setHeader('Retry-After', String(retryAfter));
            res.setHeader('X-RateLimit-Limit', String(maxRequests));
            res.setHeader('X-RateLimit-Remaining', '0');
            res.setHeader('X-RateLimit-Reset', String(Math.ceil(current.resetAt / 1000)));

            return res.status(429).json({
                error: 'Rate limit exceeded',
                type: 'rate_limiting',
                details: `${name} only allows ${maxRequests} requests every ${Math.round(windowMs / 1000)} seconds`,
                retry_after_seconds: retryAfter
            });
        }

        current.count += 1;
        res.setHeader('X-RateLimit-Limit', String(maxRequests));
        res.setHeader('X-RateLimit-Remaining', String(maxRequests - current.count));
        res.setHeader('X-RateLimit-Reset', String(Math.ceil(current.resetAt / 1000)));
        return next();
    };
};

const createThrottler = ({
    name,
    intervalMs,
    keyGenerator = getDeviceOrIpKey
}) => {
    const store = new Map();
    throttleStores.set(name, store);

    return (req, res, next) => {
        const now = Date.now();
        const key = keyGenerator(req);
        const lastAcceptedAt = store.get(key);

        if (lastAcceptedAt && now - lastAcceptedAt < intervalMs) {
            const retryAfterMs = intervalMs - (now - lastAcceptedAt);
            res.setHeader('Retry-After', String(Math.ceil(retryAfterMs / 1000)));

            return res.status(429).json({
                error: 'Request throttled',
                type: 'throttling',
                details: `${name} only accepts one request every ${intervalMs} milliseconds`,
                retry_after_ms: retryAfterMs
            });
        }

        store.set(key, now);
        return next();
    };
};

export const deviceReadRateLimiter = createRateLimiter({
    name: 'Device read endpoint',
    windowMs: 60 * 1000,
    maxRequests: 60
});

export const deviceRegistrationThrottler = createThrottler({
    name: 'Device registration endpoint',
    intervalMs: 1000,
    keyGenerator: getClientIp
});

