# OAuth BFF Demo - Production-Ready Architecture

**Backend-for-Frontend (BFF) pattern with Spring Boot + React, HIPAA-compliant same-site security.**

## 🎯 What This Demo Provides

This is a **production-ready** demonstration showing:

✅ **Same-Site Architecture** - Exactly like production (frontend + backend on same origin)  
✅ **Session-Based Auth** - Secure server-side sessions with GitHub OAuth  
✅ **CSRF Protection** - Automatic token handling via cookies  
✅ **HIPAA Ready** - 15-min session timeout, secure cookies, proper security headers  
✅ **Docker Setup** - nginx reverse proxy for same-origin deployment  
✅ **Zero CORS** - No CORS configuration needed (same-site = no cross-origin requests)

## 🏗️ Architecture

```
User Browser (http://localhost:3000)
         │
         ▼
    ┌─────────┐
    │  nginx  │  ← Reverse Proxy
    │  :3000  │
    └────┬────┘
         │
         ├──────────────┬───────────────┐
         │              │               │
    Frontend (/)   Backend (/api/*) OAuth (/oauth2/*)
    React SPA      Spring Boot      Spring Security
         │              │               │
         └──────────────┴───────────────┘
              SAME ORIGIN
        (no CORS needed!)
```

## 🚀 Quick Start

### 1. Setup GitHub OAuth App

1. Go to [GitHub Settings → Developer Settings → OAuth Apps](https://github.com/settings/developers)
2. Click "New OAuth App"
3. Configure:
   - **Application name**: BFF Demo
   - **Homepage URL**: `http://localhost:3000`
   - **Callback URL**: `http://localhost:3000/login/oauth2/code/github`
4. Save **Client ID** and **Client Secret**

### 2. Configure Environment

Create `.env` file in project root:

```bash
GITHUB_CLIENT_ID=your_client_id_here
GITHUB_CLIENT_SECRET=your_client_secret_here
```

### 3. Build and Run

```bash
# Install frontend dependencies
cd client
npm install
npm run build
cd ..

# Start with Docker Compose
docker-compose up --build
```

### 4. Access the App

Open: **http://localhost:3000**

Click "Login with GitHub" → Authorize → See your GitHub profile!

## 📁 Project Structure

```
oauth-bff/
├── client/                     # React frontend
│   ├── src/
│   │   ├── services/
│   │   │   ├── api.js         # CSRF-aware API client
│   │   │   └── auth.js        # Authentication service
│   │   ├── hooks/
│   │   │   └── useAuth.js     # Auth state management
│   │   ├── pages/
│   │   │   ├── HomePage.jsx   # Landing page
│   │   │   └── Dashboard.jsx  # User profile
│   │   └── App.jsx
│   ├── package.json
│   ├── vite.config.js
│   └── Dockerfile
│
├── server/                     # Spring Boot backend  
│   ├── src/main/java/
│   │   └── com/example/server/
│   │       ├── SecurityConfig.java          # Security configuration
│   │       ├── SpaCsrfTokenRequestHandler.java
│   │       ├── CsrfCookieFilter.java
│   │       ├── UserController.java          # API endpoints
│   │       └── HomeController.java
│   ├── src/main/resources/
│   │   ├── application.yml           # Base config
│   │   ├── application-dev.yml       # Local dev (no Docker)
│   │   ├── application-docker.yml    # Docker dev
│   │   └── application-prod.yml      # Production
│   ├── build.gradle
│   └── Dockerfile
│
├── docker-compose.yml          # Full stack orchestration
├── nginx.conf                  # Reverse proxy config
├── .env                        # Secrets (create from .env.example)
└── README.md                   # This file
```

## 🔐 Security Features Explained

### Same-Site = Production-Ready

**Why same-site is critical for HIPAA:**

```
Different Origins (NOT same-site):
  Frontend:  http://localhost:5173
  Backend:   http://localhost:8080
  ❌ Requires CORS
  ❌ Cookies need SameSite=None (less secure)
  ❌ Different from production

Same Origin (this demo):
  Everything:  http://localhost:3000
  ✅ No CORS needed
  ✅ Cookies work perfectly (SameSite=Lax)
  ✅ EXACTLY like production setup
```

### CSRF Protection Without /api/csrf Endpoint

**You might wonder: "Where do I get the CSRF token?"**

**Answer:** You don't need a special endpoint!

1. Backend sends `XSRF-TOKEN` cookie automatically via `CsrfCookieFilter`
2. Frontend reads it from `document.cookie`
3. Frontend sends it in `X-XSRF-TOKEN` header on POST/PUT/DELETE
4. Backend validates token matches

**No /api/csrf endpoint needed** - the cookie IS the token delivery mechanism!

See [client/src/services/api.js](client/src/services/api.js) for implementation.

### Session Cookies

- `JSESSIONID` - Session ID (HttpOnly=true, can't read with JavaScript)
- `XSRF-TOKEN` - CSRF token (HttpOnly=false, readable for security header)

Both are SameSite=Lax, meaning they're only sent to same origin.

## 🔄 Development Modes

### Mode 1: Docker (Same-Site, Production-Like) ← **RECOMMENDED FOR HIPAA**

```bash
cd client && npm run build && cd ..
docker-compose up --build
```

Access: `http://localhost:3000`

- nginx serves frontend + proxies backend
- Same origin for everything
- Matches production exactly

### Mode 2: Separate Servers (CORS required)

**Backend:**
```bash
cd server
./gradlew bootRun  # Runs on :8080
```

**Frontend:**
```bash
cd client
npm run dev  # Runs on :5173
```

**GitHub OAuth Callback:** `http://localhost:8080/login/oauth2/code/github`

⚠️ **Not same-site** - requires CORS configuration (less secure)

## 📊 Differences: Development vs Production

| Feature | Docker Dev | Production |
|---------|-----------|------------|
| Protocol | HTTP | HTTPS (required) |
| Domain | localhost:3000 | app.example.com |
| Same-origin | ✅ Yes | ✅ Yes |
| nginx | ✅ Yes | ✅ Yes (or ALB) |
| Secure cookies | ❌ No (HTTP) | ✅ Yes (HTTPS) |
| Session timeout | 15 min | 15 min |
| CORS | ❌ Not needed | ❌ Not needed |

**Key Point:** Docker dev is HTTP instead of HTTPS. Everything else is identical to production!

## 🏥 HIPAA Compliance Features

✅ **Same-site architecture** (minimal attack surface)  
✅ **Session timeout** (15 minutes of inactivity)  
✅ **Secure session management** (server-side storage)  
✅ **CSRF protection** (all state-changing operations)  
✅ **HttpOnly cookies** (prevents XSS cookie theft)  
✅ **Security headers** (CSP, X-Frame-Options, HSTS in production)  

**Still needed for full HIPAA compliance:**
- HTTPS in production (required!)
- Audit logging for all PHI access
- Role-based access control (RBAC)
- Data encryption at rest
- Multi-factor authentication (MFA)

See [server/docs/SECURITY_CONCEPTS.md](server/docs/SECURITY_CONCEPTS.md) for complete explanation.

## 🚢 Production Deployment

See [server/docs/DEPLOYMENT_GUIDE.md](server/docs/DEPLOYMENT_GUIDE.md) for:

- nginx SSL configuration
- AWS/cloud deployment
- Let's Encrypt certificates
- Redis session storage
- CI/CD pipeline examples

**TL;DR for production:**

```nginx
server {
    listen 443 ssl;
    server_name app.example.com;
    
    location / {
        root /var/www/app/client/dist;
    }
    
    location /api/ {
        proxy_pass http://localhost:8080/api/;
    }
}
```

Build frontend → Deploy to /var/www → Run Spring Boot → Configure nginx → Done!

## 🛠️ Development Commands

```bash
# Backend (standalone)
cd server
./gradlew bootRun

# Frontend (standalone)
cd client
npm install
npm run dev

# Docker (recommended)
docker-compose up --build
docker-compose down

# View logs
docker-compose logs -f backend
docker-compose logs -f nginx

# Rebuild after changes
docker-compose up --build
```

## 🧪 Testing the Flow

1. **Open** `http://localhost:3000`
2. **Click** "Login with GitHub"
3. **Authorize** app on GitHub (first time)
4. **Redirected** to `/dashboard`
5. **See** your GitHub profile
6. **Check cookies** in DevTools:
   - `JSESSIONID` (HttpOnly: true)
   - `XSRF-TOKEN` (HttpOnly: false)
7. **Logout** - session cleared
8. **Wait 15 min** - session expires (HIPAA timeout)

## 🐛 Troubleshooting

### "Cookies not being set"
- Make sure using `http://localhost:3000` (not :5173 or :8080)
- Check browser DevTools → Application → Cookies
- Verify docker-compose is running

### "GitHub OAuth fails"
- Check callback URL in GitHub app: `http://localhost:3000/login/oauth2/code/github`
- Verify `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` in `.env`
- Check backend logs: `docker-compose logs -f backend`

### "401 Unauthorized on /api/user"
- Not logged in yet
- Session expired (15 min timeout)
- Cookies cleared

### "403 Forbidden on POST"
- CSRF token missing
- Check `X-XSRF-TOKEN` header is sent
- Check `XSRF-TOKEN` cookie exists

## 📚 Documentation

- [README.md](README.md) - This file
- [server/README.md](server/README.md) - Backend details
- [docs/SECURITY_CONCEPTS.md](server/docs/SECURITY_CONCEPTS.md) - Security deep dive
- [docs/DEPLOYMENT_GUIDE.md](server/docs/DEPLOYMENT_GUIDE.md) - Production deployment

## 🤝 Contributing

This is a demonstration project. Feel free to use as a template for your own BFF implementations.

## 📝 License

MIT - Use freely for your projects.

---

**Questions?** Check the comprehensive documentation in `server/docs/` or open an issue.

**Ready for production?** Follow the deployment guide and enable HTTPS!
