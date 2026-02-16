# OAuth BFF (Backend-for-Frontend) Demo

A production-ready demonstration of the Backend-for-Frontend (BFF) pattern using Spring Boot and OAuth2 with GitHub authentication.

## 🏗️ Architecture Overview

This application implements the BFF security pattern where:
- **Backend** (Spring Boot) manages authentication, sessions, and API security
- **Frontend** (React - to be created) handles UI and user experience
- **OAuth Provider** (GitHub) handles user authentication

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│   React     │ HTTPS   │ Spring Boot  │  OAuth  │   GitHub    │
│   (SPA)     ├────────►│     (BFF)    ├────────►│   OAuth2    │
│  :5173      │ CORS    │    :8080     │         │             │
└─────────────┘         └──────────────┘         └─────────────┘
      │                         │
      └─────────────────────────┘
         Session Cookies
         (JSESSIONID, XSRF-TOKEN)
```

## 🔒 Security Features

### Session-Based Authentication
- OAuth2 login with GitHub
- Server-side session storage
- Secure, HttpOnly session cookies
- Session fixation protection
- Automatic session timeout (15min prod / 30min dev)

### CSRF Protection
- Cookie-based CSRF tokens (XSRF-TOKEN)
- SPA-friendly token handler
- Automatic token generation and validation
- BREACH attack protection (XOR encoding)

### CORS Configuration
- Development: Allows localhost:5173 (Vite dev server)
- Production: Configurable for same-origin or specific domains
- Credentials support for cookie-based auth

### Security Headers
- Content Security Policy (CSP)
- HTTP Strict Transport Security (HSTS)
- X-Frame-Options (clickjacking protection)
- X-Content-Type-Options (MIME sniffing protection)

### HIPAA Compliance Ready
- Short session timeouts
- Secure cookie flags (production)
- Session management (single session per user)
- HTTPS enforced (production)
- Audit logging hooks (to be implemented)

## 📋 Prerequisites

- Java 21+
- Gradle
- GitHub OAuth App credentials
- Node.js 18+ (for frontend - TBD)

## 🚀 Quick Start

### 1. Register GitHub OAuth App

1. Go to GitHub → Settings → Developer Settings → [OAuth Apps](https://github.com/settings/developers)
2. Click "New OAuth App"
3. Fill in:
   - **Application name**: BFF Demo
   - **Homepage URL**: `http://localhost:8080`
   - **Authorization callback URL**: `http://localhost:8080/login/oauth2/code/github`
4. Save the **Client ID** and generate a **Client Secret**

### 2. Set Environment Variables

```bash
export GITHUB_CLIENT_ID=your_client_id_here
export GITHUB_CLIENT_SECRET=your_client_secret_here
```

### 3. Run the Application

**Development Mode** (default):
```bash
./gradlew bootRun
```

**Production Mode**:
```bash
./gradlew bootRun --args='--spring.profiles.active=prod'
```

### 4. Test the Endpoints

**Check API is running**:
```bash
curl http://localhost:8080/
```

**Login with GitHub**:
```
Open browser: http://localhost:8080/oauth2/authorization/github
```

**Get user info** (after login):
```bash
curl -b cookies.txt http://localhost:8080/api/user
```

**Logout**:
```bash
curl -X POST -b cookies.txt http://localhost:8080/api/logout
```

## 📁 Project Structure

```
server/
├── src/main/java/com/example/server/
│   ├── OauthServerApplication.java      # Main application entry point
│   ├── SecurityConfig.java              # Security & CORS configuration
│   ├── SpaCsrfTokenRequestHandler.java  # SPA CSRF token handling
│   ├── CsrfCookieFilter.java            # Force CSRF cookie generation
│   ├── UserController.java              # User API endpoints
│   └── HomeController.java              # Home & utility endpoints
├── src/main/resources/
│   ├── application.yml                  # Base configuration
│   ├── application-dev.yml              # Development profile
│   └── application-prod.yml             # Production profile
├── build.gradle                         # Dependencies & build config
└── README.md                            # This file
```

## 🔌 API Endpoints

### Public Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | API info and available endpoints |
| GET | `/api/csrf` | Get CSRF token explicitly |
| GET | `/oauth2/authorization/github` | Start GitHub OAuth flow |

### Protected Endpoints (Require Authentication)

| Method | Endpoint | Description | CSRF Required |
|--------|----------|-------------|---------------|
| GET | `/api/user` | Get authenticated user info | No (GET) |
| POST | `/api/logout` | Logout and clear session | Yes |

## 🌍 Development vs Production

### Development Environment

**Characteristics**:
- HTTP (not HTTPS)
- Frontend and backend on different ports
- CORS enabled for localhost:5173
- Longer session timeout (30 minutes)
- Verbose logging
- Cookie `secure` flag = false

**Run**:
```bash
./gradlew bootRun
# or explicitly:
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Production Environment

**Characteristics**:
- HTTPS required (via reverse proxy or native)
- Same-origin deployment (no CORS needed)
- Short session timeout (15 minutes for HIPAA)
- Minimal logging
- Cookie `secure` flag = true
- HSTS headers enabled

**Deployment Options**:

**Option A: Reverse Proxy** (Recommended)
```nginx
# nginx configuration
server {
    listen 443 ssl;
    server_name app.example.com;
    
    # Frontend (React build)
    location / {
        root /var/www/frontend/build;
        try_files $uri /index.html;
    }
    
    # Backend (proxy to Spring Boot)
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
    
    location /oauth2/ {
        proxy_pass http://localhost:8080/oauth2/;
        # ... same proxy headers
    }
    
    location /login/ {
        proxy_pass http://localhost:8080/login/;
        # ... same proxy headers
    }
}
```

**Option B: Spring Boot Native HTTPS**
- Uncomment SSL configuration in `application-prod.yml`
- Generate SSL certificate (Let's Encrypt recommended)
- Deploy frontend + backend separately with CORS

**Run**:
```bash
export SPRING_PROFILES_ACTIVE=prod
export GITHUB_CLIENT_ID=prod_client_id
export GITHUB_CLIENT_SECRET=prod_client_secret
java -jar build/libs/server-0.0.1-SNAPSHOT.jar
```

## 🔐 Security Concepts Explained

### Why Same-Site Cookies?

**Same-Site** means the cookie is only sent when:
- The request comes from the same scheme + domain + port
- For OAuth redirects, use `SameSite=Lax` (allows top-level navigation)

**Development**: `localhost:8080` and `localhost:5173` are **different sites** (different ports = different origins), so cookies are sent but CORS is needed.

**Production**: Deploy both on `app.example.com` via reverse proxy = **same site** = no CORS needed.

### Why CSRF Tokens?

**CSRF (Cross-Site Request Forgery)** attack:
1. Victim logs into your app
2. Victim visits malicious site
3. Malicious site makes victim's browser POST to your app
4. Without CSRF protection, the request succeeds (cookies sent automatically)

**CSRF Token Protection**:
- Server generates random token
- Token sent in cookie (attacker can trigger this)
- Frontend reads token from cookie
- Frontend sends token in header
- Attacker CAN'T read the token (CORS blocks them)
- Attacker CAN'T send the header value

**Why GET doesn't need CSRF**:
- CSRF attack makes the browser send a request
- But CORS prevents attacker from **reading the response**
- GET is read-only, so even if triggered, no damage done
- POST/PUT/DELETE change state, so must be protected

### HTTP vs HTTPS

**Development** (HTTP OK):
- Local testing only
- Faster development (no cert setup)
- Easy debugging

**Production** (HTTPS REQUIRED):
- **HIPAA requires HTTPS**
- Protects data in transit (encryption)
- Prevents man-in-the-middle attacks
- Allows secure cookies
- Required for `SameSite=None` (if needed)

## 🔧 Configuration

### GitHub OAuth Scopes

Current scopes (minimal):
- `read:user` - Read user profile
- `user:email` - Read user email

Add more scopes in `application-prod.yml`:
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            scope: read:user,user:email,repo
```

### Session Timeout

Configure in `application-{profile}.yml`:
```yaml
spring:
  session:
timeout: 15m  # HIPAA compliance: 15 minutes
```

### CORS Origins

Configure in `application-{profile}.yml`:
```yaml
cors:
  allowed-origins: https://app.example.com,https://mobile.example.com
```

## 📚 Next Steps

### Frontend Development (React)

1. Create React app with Vite
2. Implement login flow
3. Handle session cookies
4. Read and send CSRF tokens
5. Build dashboard with user info

See `docs/FRONTEND_GUIDE.md` (to be created)

### Production Deployment

1. Set up HTTPS (reverse proxy or native)
2. Configure production OAuth app in GitHub
3. Set environment variables
4. Deploy with `prod` profile
5. Configure session store (Redis for multi-instance)
6. Set up monitoring and logging
7. Implement audit trail for HIPAA

See `docs/DEPLOYMENT_GUIDE.md` (to be created)

### Additional Features

- [ ] Role-based access control (RBAC)
- [ ] Multi-factor authentication (MFA)
- [ ] Session store (Redis/database)
- [ ] Rate limiting
- [ ] Audit logging
- [ ] API documentation (Swagger/OpenAPI)
- [ ] Health checks and monitoring
- [ ] Remember me functionality
- [ ] Account linking (multiple OAuth providers)

## 📖 Additional Resources

- [Spring Security OAuth2 Client](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html)
- [OWASP CSRF Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [HIPAA Security Rule](https://www.hhs.gov/hipaa/for-professionals/security/index.html)
- [OAuth 2.0 RFC 6749](https://tools.ietf.org/html/rfc6749)

## 📝 License

This is a demonstration project. Use at your own risk.

## 👥 Authors

Your Team - Backend-for-Frontend Demo Project
