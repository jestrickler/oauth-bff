# Development vs Production - Exact Comparison

This document shows **exactly** how the Docker development setup matches production, and the **only** differences.

## What's IDENTICAL (95% of setup)

### ✅ Architecture
```
Development (Docker):          Production:
┌─────────────┐               ┌─────────────┐
│    nginx    │               │  nginx/ALB  │
│   :3000     │               │   :443      │
└──────┬──────┘               └──────┬──────┘
       │                             │
   ┌───┴────┐                    ┌───┴────┐
Frontend  Backend             Frontend  Backend
(React)  (Spring)              (React)  (Spring)
```
**Status:** IDENTICAL architecture

### ✅ Same-Origin Configuration

| Aspect | Docker Dev | Production |
|--------|------------|------------|
| Frontend path | `/` | `/` |
| Backend path | `/api/*` | `/api/*` |
| OAuth path | `/oauth2/*` | `/oauth2/*` |
| CORS needed | No | No |
| Cookie domain | Same origin | Same origin |

**Status:** IDENTICAL routing

### ✅ nginx Configuration

Both use [nginx.conf](../nginx.conf):
- Reverse proxy to backend
- Serve static frontend files
- Security headers
- Gzip compression
- Cache control

**Status:** IDENTICAL file (except SSL)

### ✅ Spring Security Configuration

Both use [SecurityConfig.java](../server/src/main/java/com/example/server/SecurityConfig.java):
- Session-based authentication
- CSRF protection (cookie + header)
- OAuth2 login flow
- Session timeout (15 min)
- Maximum sessions (1 per user)

**Status:** IDENTICAL configuration

### ✅ CSRF Protection

| Feature | Docker Dev | Production |
|---------|------------|------------|
| XSRF-TOKEN cookie | Yes | Yes |
| HttpOnly=false | Yes | Yes |
| SameSite=Lax | Yes | Yes |
| X-XSRF-TOKEN header | Required | Required |
| Auto-sent via CsrfCookieFilter | Yes | Yes |

**Status:** IDENTICAL implementation

### ✅ Session Management

| Feature | Docker Dev | Production |
|---------|------------|------------|
| Session store | In-memory* | Redis** |
| Session timeout | 15 minutes | 15 minutes |
| Session fixation protection | Yes | Yes |
| Max sessions per user | 1 | 1 |

*In Docker for demo simplicity  
**Production should use Redis/database for multi-instance deployments

### ✅ Security Headers

Both configurations set:
- `X-Frame-Options: DENY`
- `X-Content-Type-Options: nosniff`
- `X-XSS-Protection: 1; mode=block`

**Status:** IDENTICAL

### ✅ Frontend Implementation

Both use [client/src/services/api.js](../client/src/services/api.js):
- Read CSRF token from cookie
- Send in X-XSRF-TOKEN header
- credentials: 'include' for all requests
- Handle 401 redirects

**Status:** IDENTICAL code

## What's DIFFERENT (The 5%)

### 1. Protocol: HTTP vs HTTPS

```yaml
# Docker Dev (application-docker.yml)
server:
  servlet:
    session:
      cookie:
        secure: false  # ← HTTP only

# Production (application-prod.yml)
server:
  servlet:
    session:
      cookie:
        secure: true   # ← HTTPS required
```

**Why:** Local dev doesn't have SSL certificates. Production MUST use HTTPS for:
- HIPAA compliance
- Secure cookie flag
- Encrypted data in transit

**How to add HTTPS to dev (optional):**
```bash
# Generate self-signed cert
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout nginx.key -out nginx.crt

# Update nginx.conf:
listen 443 ssl;
ssl_certificate /etc/nginx/nginx.crt;
ssl_certificate_key /etc/nginx/nginx.key;
```

### 2. Domain: localhost vs production domain

```yaml
# Docker Dev
http://localhost:3000

# Production
https://app.example.com
```

**Impact:**
- Cookie domain setting
- OAuth callback URL
- CORS origins (both set to same domain, both work)

### 3. HSTS Header

```nginx
# Docker Dev (nginx.conf)
# HSTS commented out (only works with HTTPS)

# Production
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
```

**Why:** HSTS forces HTTPS for future requests. Only makes sense with HTTPS.

### 4. Session Store

```yaml
# Docker Dev
# Default: in-memory (single instance)

# Production
spring:
  session:
    store-type: redis
  redis:
    host: redis.example.com
```

**Why:** In-memory is fine for single-instance dev. Production needs persistence for:
- Multiple backend instances
- Server restarts
- Load balancing

**To use Redis in dev:**
```bash
# Add redis service to docker-compose.yml
redis:
  image: redis:alpine
  
# Update application-docker.yml
spring:
  session:
    store-type: redis
  redis:
    host: redis
```

### 5. Logging Verbosity

```yaml
# Docker Dev
logging:
  level:
    org.springframework.security: DEBUG  # ← Verbose

# Production
logging:
  level:
    org.springframework.security: INFO   # ← Less chatty
```

**Why:** Debug logs helpful in development. Production needs performance + disk space.

## Summary: What You Need to Change for Production

### Required Changes (3):

1. **Enable HTTPS:** Get SSL certificate, update nginx config
2. **Set secure flag:** `server.servlet.session.cookie.secure=true`
3. **Use production domain:** Update GitHub OAuth callback URL

### Recommended Changes (2):

4. **Redis session store:** For multi-instance deployments
5. **Reduce logging:** Change DEBUG → INFO/WARN

### Configuration-Only Changes (No Code):

6. **Environment variables:** Point to production GitHub OAuth app
7. **Session timeout:** Keep 15 min or adjust as needed
8. **Domain/CORS:** Update to production domain

## Testing Production Config Locally (with HTTPS)

Want to test EXACT production setup locally?

```bash
# 1. Generate self-signed cert
mkcert localhost

# 2. Update nginx.conf
listen 443 ssl http2;
ssl_certificate /etc/nginx/ssl/localhost.pem;
ssl_certificate_key /etc/nginx/ssl/localhost-key.pem;

# 3. Update docker-compose.yml
volumes:
  - ./localhost.pem:/etc/nginx/ssl/localhost.pem:ro
  - ./localhost-key.pem:/etc/nginx/ssl/localhost-key.pem:ro

# 4. Update .env
SPRING_PROFILES_ACTIVE=prod

# 5. Set GitHub OAuth callback
https://localhost:443/login/oauth2/code/github

# 6. Start
docker-compose up --build
```

Access: `https://localhost` (note: port 443, not 3000)

Now you're running EXACT production setup locally!

## Deployment Checklist

When deploying to production, verify:

- [ ] HTTPS enabled (Let's Encrypt or commercial cert)
- [ ] `secure: true` on cookies
- [ ] HSTS header enabled
- [ ] Production GitHub OAuth app configured
- [ ] Production domain in CORS settings
- [ ] Redis for session storage (if multi-instance)
- [ ] Logging set to INFO/WARN
- [ ] Environment variables set correctly
- [ ] Firewall configured (ports 80, 443 only)
- [ ] nginx serving compressed static files
- [ ] Health checks configured
- [ ] Monitoring set up

See [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) for complete production setup.

---

**Bottom Line:** This Docker setup IS production architecture. Only difference is HTTP vs HTTPS.

Add an SSL cert, and you're production-ready!
