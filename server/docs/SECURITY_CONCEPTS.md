# Security Concepts Deep Dive

A comprehensive explanation of the security mechanisms in this BFF application.

## Table of Contents

1. [HTTPS: Development vs Production](#https-development-vs-production)
2. [Same-Site vs Same-Origin vs CORS](#same-site-vs-same-origin-vs-cors)
3. [CSRF Protection Explained](#csrf-protection-explained)
4. [Session-Based Authentication](#session-based-authentication)
5. [HIPAA Compliance Considerations](#hipaa-compliance-considerations)
6. [Attack Scenarios and Mitigations](#attack-scenarios-and-mitigations)

---

## HTTPS: Development vs Production

### Why NOT HTTPS in Development?

**Development (HTTP is OK)**:
```
http://localhost:8080 (backend)
http://localhost:5173 (frontend)
```

**Reasons**:
1. **Faster iteration**: No certificate generation/renewal
2. **Easier debugging**: Can inspect traffic without SSL/TLS decryption
3. **Local only**: Not exposed to internet, no man-in-the-middle risk
4. **Self-signed cert issues**: Browser warnings, trust issues, CA setup

**When to use HTTPS in dev**:
- Testing HTTPS-specific features (HSTS, secure cookies)
- Testing cross-browser compatibility
- Developing PWAs (require HTTPS)
- Using certain browser APIs (geolocation, camera, etc.)

### HTTPS in Production (REQUIRED)

**Why HTTPS is MANDATORY for production**:

1. **Encryption in Transit**
   ```
   HTTP:  Client → [PLAINTEXT] → Server
   HTTPS: Client → [ENCRYPTED] → Server
   ```
   - Without HTTPS, session cookies transmitted in plaintext
   - Attacker on network can steal session ID
   - Instant account takeover

2. **HIPAA Requirement**
   - "Protected Health Information (PHI) must be encrypted in transit"
   - HTTP = violation = fines
   - HTTPS required for compliance

3. **Secure Cookies**
   ```yaml
   server:
     servlet:
       session:
         cookie:
           secure: true  # Cookie only sent over HTTPS
   ```
   - Prevents cookie theft on mixed HTTP/HTTPS pages
   - Browser enforces: cookie won't be sent over HTTP

4. **Trust Indicators**
   - Padlock icon in browser
   - Green address bar (EV certs)
   - User confidence

### How HTTPS is Configured (Production)

**Option A: Reverse Proxy** (RECOMMENDED)

```
Internet → nginx (HTTPS) → Spring Boot (HTTP)
          :443                :8080 (internal)
```

**Why this is best**:
- SSL termination at proxy (nginx/Apache handles encryption)
- Backend stays simple (HTTP internally)
- Easier certificate management (one place)
- Better performance (nginx optimized for SSL)
- Load balancing capabilities

**nginx configuration**:
```nginx
server {
    listen 443 ssl http2;
    server_name app.example.com;
    
    ssl_certificate /etc/letsencrypt/live/app.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/app.example.com/privkey.pem;
    
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

**Spring Boot configuration**:
```yaml
server:
  forward-headers-strategy: framework  # Trust X-Forwarded-Proto
```

**Option B: Spring Boot Native HTTPS**

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_PASSWORD}
    key-store-type: PKCS12
```

**When to use**:
- No reverse proxy available
- Microservices communicating over HTTPS
- Simple deployment scenario

**Downsides**:
- Certificate renewal in application
- Worse performance than nginx
- Can't easily load balance

---

## Same-Site vs Same-Origin vs CORS

This is the most confusing aspect of web security, so let's break it down.

### Definitions

**Same-Origin**: Scheme + Domain + Port MUST match exactly

```
https://app.example.com:443/page1
https://app.example.com:443/page2  ✅ Same origin

https://app.example.com:443
https://api.example.com:443         ❌ Different origin (subdomain)

http://app.example.com:80
https://app.example.com:443         ❌ Different origin (scheme)

http://localhost:8080
http://localhost:5173               ❌ Different origin (port)
```

**Same-Site**: More lenient, focuses on "registrable domain"

```
https://app.example.com
https://api.example.com             ✅ Same site (.example.com)

https://app.example.com
https://app.different.com           ❌ Different site

http://localhost:8080
http://localhost:5173               ❌ Different site (different ports)
```

**Wait, localhost ports are different sites?**
Yes! For cookie purposes:
- Different ports = different sites
- But localhost is special: might vary by browser

### Why This Matters for Cookies

**SameSite Cookie Attribute**:

```
Set-Cookie: JSESSIONID=abc123; SameSite=Lax
```

**SameSite=Strict**:
- Cookie sent only if request is from same site
- Never sent on cross-site navigation
- Breaks OAuth redirects (GitHub → your app)

**SameSite=Lax** (Our Choice):
- Cookie sent on same-site requests ✅
- Cookie sent on top-level navigation (OAuth redirect) ✅
- Cookie NOT sent on cross-site subrequests (AJAX from other sites) ❌

**SameSite=None**:
- Cookie sent everywhere
- REQUIRES `Secure` flag (HTTPS only)
- Less secure, use only when necessary

### Development Scenario

```
Frontend:  http://localhost:5173  (Vite)
Backend:   http://localhost:8080  (Spring Boot)
```

**Problem**:
- Different origins → CORS required
- Different ports → Technically different sites
- Cookies still work because `SameSite=Lax` allows it

**How cookies work**:
1. User logs in at `localhost:8080`
2. Server sets cookie: `Set-Cookie: JSESSIONID=abc; SameSite=Lax; Path=/`
3. Frontend makes request from `localhost:5173` to `localhost:8080`
4. Browser sends cookie (same domain, top-level or direct request)
5. Backend validates session ✅

**But CORS is still needed**:
- Browser sends cookie ✅
- But JavaScript can't READ the response without CORS ❌
- CORS headers allow reading the response

### Production Scenario (BFF Pattern)

```
All traffic through: https://app.example.com
Frontend: /            → static files
Backend:  /api/*       → proxied to :8080
```

**Advantages**:
- Same origin → No CORS needed ✅
- Same site → Cookies work perfectly ✅
- More secure (smaller attack surface) ✅

**How reverse proxy works**:
```nginx
location / {
    # Serve React build
    root /var/www/frontend/build;
}

location /api/ {
    # Proxy to Spring Boot
    proxy_pass http://localhost:8080/api/;
}
```

From user's perspective: Everything is `app.example.com`

### CORS: Cross-Origin Resource Sharing

**What CORS protects against**:

Without CORS:
```
https://evil.com has JavaScript:
fetch('https://yourbank.com/api/transfer?to=attacker&amount=1000000')
```

If your bank allowed CORS from `*`, the website could:
1. Make request using YOUR cookies
2. READ the response
3. Steal your data

**With CORS properly configured**:
```
Browser: "Can evil.com read responses from yourbank.com?"
Server: "Allowed origins: yourbank.com only"
Browser: "Blocked" ❌
```

**Our CORS configuration**:
```java
configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173"));
configuration.setAllowCredentials(true);
```

**What this means**:
- Only `localhost:5173` can make JavaScript requests ✅
- And read the responses ✅
- Cookies are sent with requests ✅
- Any other origin is blocked ❌

**Production CORS**:

**Option A: Same origin (BFF)** - NO CORS NEEDED
```java
// Empty or same origin
configuration.setAllowedOrigins(Arrays.asList());
```

**Option B: Separate domains**
```java
configuration.setAllowedOrigins(Arrays.asList("https://app.example.com"));
```

**NEVER in production**:
```java
configuration.setAllowedOrigins(Arrays.asList("*"));  // ❌ DANGEROUS
configuration.setAllowCredentials(true);  // ❌ Won't even work together
```

---

## CSRF Protection Explained

### The Attack

**Scenario**: You're logged into `yourbank.com`

1. You visit `evil.com`
2. `evil.com` has this HTML:
```html
<form action="https://yourbank.com/api/transfer" method="POST">
  <input name="to" value="attacker">
  <input name="amount" value="1000000">
</form>
<script>
  document.forms[0].submit();
</script>
```
3. Your browser automatically sends your `yourbank.com` cookies
4. Bank sees valid session, transfers money ❌

**Why cookies are the problem**:
- Browsers automatically send cookies with requests
- Even cross-site requests
- Attacker can't READ cookies, but browser sends them anyway

### CSRF Token Solution

**How it works**:

```
1. Login → Server generates CSRF token: "abc123"
2. Server sends token in cookie: XSRF-TOKEN=abc123
3. Frontend reads cookie (JavaScript can read it)
4. Frontend makes POST:
   Headers: {
     "X-XSRF-TOKEN": "abc123",
     "Cookie": "JSESSIONID=xyz; XSRF-TOKEN=abc123"
   }
5. Server validates: token in header matches token in cookie ✅
```

**Why attacker can't do this**:

```
evil.com tries to attack:
1. evil.com can trigger browser to send cookies ✅
2. But evil.com JavaScript can't READ yourbank.com cookies ❌
3. CORS blocks evil.com from reading responses ❌
4. evil.com doesn't know the token value ❌
5. Can't set the X-XSRF-TOKEN header ❌
6. Attack fails ✅
```

### Why GET Doesn't Need CSRF

**GET requests should be idempotent** (read-only):
- Should never change state
- Should never transfer money, delete data, etc.
- Only retrieve information

**Even if attacker triggers GET**:
```html
<img src="https://yourbank.com/api/account" />
```

- Request is sent ✅
- Response is returned ✅
- But attacker can't READ the response (CORS blocks it) ❌
- No damage done ✅

**Proper API design**:
- GET = read-only, no CSRF needed
- POST/PUT/DELETE = state-changing, CSRF required

### Our CSRF Implementation

**1. CookieCsrfTokenRepository**:
```java
.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
```

- Stores token in `XSRF-TOKEN` cookie
- `HttpOnly=false` so JavaScript can read it
- Validates token from `X-XSRF-TOKEN` header

**2. SpaCsrfTokenRequestHandler**:
- Handles both cookie-based (SPA) and form-based tokens
- XOR encoding for BREACH protection
- Compatible with server-rendered forms

**3. CsrfCookieFilter**:
- Forces cookie to be sent on first request
- Without this, cookie is only sent when token is accessed
- SPAs need token immediately

### CSRF Security Trade-offs

**HttpOnly=false on XSRF-TOKEN cookie**:

Pros:
- JavaScript can read token ✅
- Can send in header ✅
- Works with SPAs ✅

Cons:
- XSS attack could steal token ⚠️
- Mitigated by Content-Security-Policy
- If XSS exists, bigger problems anyway

**Alternative**: Double Submit Cookie (what we use)
- Token in cookie + token in header
- Simpler than server-side token storage
- Stateless (scales better)

---

## Session-Based Authentication

### Why Sessions (Not JWT)?

**JWTs are great for**:
- Microservices
- Mobile apps
- API-to-API communication

**Sessions are better for BFFs** (our use case):
- Revocable (logout immediately works)
- Smaller payload (just session ID in cookie)
- Server-side control
- Easier to manage OAuth tokens

### How Our Sessions Work

**1. Authentication (OAuth with GitHub)**:
```
User clicks "Login with GitHub"
  ↓
Redirected to GitHub OAuth page
  ↓
User authorizes app
  ↓
GitHub redirects back with code
  ↓
Spring Security exchanges code for access token
  ↓
Fetches user info from GitHub API
  ↓
Creates OAuth2User object
  ↓
Stores OAuth2User in session
  ↓
Generates session ID: "xyz789"
  ↓
Sets cookie: JSESSIONID=xyz789; HttpOnly; Secure; SameSite=Lax
  ↓
Redirects to frontend dashboard
```

**2. Subsequent Requests**:
```
Frontend makes request to /api/user
  ↓
Browser automatically sends JSESSIONID cookie
  ↓
Spring Security looks up session by ID
  ↓
Retrieves OAuth2User from session
  ↓
Injects into @AuthenticationPrincipal
  ↓
Controller returns user data
```

**3. Logout**:
```
POST /api/logout
  ↓
Spring Security invalidates session
  ↓
Deletes session from store
  ↓
Clears cookies
  ↓
Session ID now invalid
```

### Session Storage

**Default: In-Memory**
```java
// Built-in, no configuration needed
// Problem: Lost on restart, doesn't scale
```

**Production: Redis/Database**
```yaml
spring:
  session:
    store-type: redis
```

Advantages:
- Survives restarts
- Scales horizontally
- Shared across instances
- Persistent

### Session Security Features

**1. Session Fixation Protection**:
```java
.sessionFixation().newSession()
```

Attack prevention:
- Attacker gives you session ID
- You log in
- Session ID changes
- Attacker's ID is now useless

**2. Concurrent Session Control**:
```java
.maximumSessions(1)
.maxSessionsPreventsLogin(false)
```

- Only one session per user
- New login kicks out old session
- Prevents credential sharing (HIPAA)

**3. Session Timeout**:
```yaml
spring:
  session:
    timeout: 15m
```

- HIPAA requirement: 15 minutes inactivity
- Auto-logout for security

---

## HIPAA Compliance Considerations

### Technical Safeguards Required

**1. Access Controls**:
- ✅ User authentication (OAuth2)
- ✅ Session management
- ⚠️ Role-based access control (to be added)
- ⚠️ Audit logs (to be added)

**2. Transmission Security**:
- ✅ HTTPS in production
- ✅ Encrypted data in transit
- ⚠️ Certificate management

**3. Integrity Controls**:
- ✅ CSRF protection
- ✅ Session validation
- ⚠️ Data integrity checks

**4. Authentication**:
- ✅ OAuth2 authentication
- ⚠️ Multi-factor authentication (recommended, to be added)
- ✅ Session timeout (15 minutes)

### Additional Requirements for PHI

**Audit Logging** (not yet implemented):
```java
public class AuditLogInterceptor extends HandlerInterceptorAdapter {
    @Override
    public void afterCompletion(HttpServletRequest request, ...) {
        // Log: who, what, when, where
        auditLog.log(
            user: getPrincipal(),
            action: request.getMethod() + " " + request.getRequestURI(),
            timestamp: Instant.now(),
            ipAddress: request.getRemoteAddr(),
            result: response.getStatus()
        );
    }
}
```

**Access Controls** (to be added):
```java
@PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
@GetMapping("/api/patient/{id}")
public Patient getPatient(@PathVariable Long id) {
    // Only doctors and admins can access
}
```

**Data Encryption at Rest** (if storing PHI):
- Database encryption
- Encrypted file storage
- Encrypted backups

---

## Attack Scenarios and Mitigations

### 1. Session Hijacking

**Attack**:
- Attacker steals JSESSIONID cookie
- Impersonates user

**How they might steal it**:
- Network sniffing (if HTTP) ❌
- XSS attack (if not HttpOnly) ❌
- Man-in-the-middle (if no HTTPS) ❌

**Our Mitigations**:
- ✅ HTTPS (production) - can't sniff
- ✅ HttpOnly cookies - can't read with JavaScript
- ✅ Secure flag (production) - only sent over HTTPS
- ✅ SameSite=Lax - not sent cross-site

### 2. CSRF Attack

**Attack**: Make user's browser perform actions

**Our Mitigations**:
- ✅ CSRF tokens on state-changing operations
- ✅ SameSite cookies (defense in depth)
- ✅ CORS prevents reading responses

### 3. XSS (Cross-Site Scripting)

**Attack**: Inject malicious JavaScript

**Our Mitigations**:
- ✅ Content-Security-Policy header
- ✅ X-XSS-Protection header
- ⚠️ Frontend must sanitize user input
- ⚠️ React escapes by default (helpful)

### 4. Man-in-the-Middle

**Attack**: Intercept traffic between client and server

**Our Mitigations**:
- ✅ HTTPS (production)
- ✅ HSTS header (forces HTTPS)
- ✅ Certificate validation

### 5. Clickjacking

**Attack**: Embed site in iframe, trick user into clicking

**Our Mitigations**:
- ✅ X-Frame-Options: DENY
- ✅ CSP frame-ancestors 'none'

### 6. Brute Force

**Attack**: Try many passwords/sessions

**Our Mitigations**:
- ⚠️ Rate limiting (to be added)
- ⚠️ Account lockout (to be added)
- ✅ OAuth delegates to GitHub (they handle this)

---

## Summary Checklist

### Development Environment ✅
- [x] HTTP is OK (localhost only)
- [x] CORS enabled for frontend
- [x] Session cookies work
- [x] CSRF protection active
- [x] Verbose logging for debugging

### Production Environment ⚠️
- [x] HTTPS configured (documented)
- [x] Secure cookies enabled (in prod profile)
- [x] Short session timeout (15 min)
- [x] Security headers (CSP, HSTS, etc.)
- [x] CORS restricted to production domain
- [ ] Rate limiting (to be added)
- [ ] Audit logging (to be added)
- [ ] MFA (to be added for HIPAA)

### Code Security ✅
- [x] All inputs validated
- [x] SQL injection prevention (using JPA)
- [x] Session management secure
- [x] CSRF tokens properly implemented
- [x] Comprehensive documentation

---

**Next**: See `FRONTEND_GUIDE.md` for React implementation details
