# BFF Migration Strategy: From Frontend OAuth to Backend-for-Frontend

This guide helps you refactor applications that currently handle OAuth directly from the frontend to use the production-ready BFF (Backend-for-Frontend) pattern.

## Problem: Frontend OAuth

### Current Architecture (The Problem)
```
┌──────────────────┐
│  Browser         │
│  Frontend App    │
│  (port 3000)    │
└────────┬─────────┘
         │
         ├─────────────────────────┐
         │                         │
         ▼                         ▼
   ┌──────────┐          ┌─────────────────┐
   │ Your API │          │  GitHub OAuth   │
   │ (port 8080)         │  (api.github)   │
   └──────────┘          └─────────────────┘
```

### Problems with Frontend OAuth:
1. **CORS complexity** - Browser blocks github.com requests from your domain
2. **Token exposure** - Access tokens loaded in browser (some XSS risk)
3. **Session management** - Frontend must manage tokens, refresh logic
4. **HIPAA issues** - Hard to enforce session timeouts, secure cookies
5. **Deployment friction** - Can't change auth without frontend redeploy

---

## Solution: BFF Pattern (Backend-for-Frontend)

### New Architecture (The Solution)
```
┌────────────────────────────┐
│  Browser                   │
│  (single origin)           │
└────────────┬───────────────┘
             │
             ▼
      ┌──────────────┐
      │   nginx      │  ← Same-site magic happens here
      │  (port 3000) │
      └──┬──────┬────┘
         │      │
    Static   APIProxy
    Files    Routes
         │      │
         ▼      ▼
      ┌──────────────────┐
      │ Backend/BFF      │
      │ (Spring Boot)    │  ← Handles OAuth
      └────────┬─────────┘
               │
               ▼
         ┌──────────────┐
         │ GitHub OAuth │
         │ (+ Session)  │
         └──────────────┘
```

---

## Step-by-Step Migration

### Phase 1: Analyze Current App (1 hour)

**Audit your frontend OAuth implementation:**

- [ ] Where is GitHub OAuth initialized? (e.g., `client_id` in config)
- [ ] How is the token stored? (localStorage? sessionStorage? Cookie?)
- [ ] Which endpoints need authentication?
- [ ] How do you refresh expired tokens?
- [ ] What user data is displayed from GitHub?
- [ ] Current session management strategy?

**Example current code you might have:**
```javascript
// OLD: Frontend doing OAuth
const clientId = 'abc123def456';
const redirectUri = `${window.location.origin}/callback`;

async function loginWithGitHub() {
  window.location.href = 
    `https://github.com/login/oauth/authorize?client_id=${clientId}&redirect_uri=${redirectUri}`;
}

// Token stored in localStorage
localStorage.setItem('github_token', accessToken);
```

### Phase 2: Create Backend OAuth Handler (2-3 hours)

**Setup Spring Security OAuth2 (like this demo):**

1. **Add dependencies** (build.gradle):
```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

2. **GitHub OAuth Config** (application.yml):
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
            scope: user,repo
          
  session:
    timeout: 15m  # Auto-logout for HIPAA
```

3. **Spring Security Config** (SecurityConfig.java):
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      .oauth2Login(oauth -> oauth
        .defaultSuccessUrl("/dashboard", true))  // After login, redirect to dashboard
      .logout(logout -> logout
        .logoutUrl("/api/logout")
        .logoutSuccessUrl("/"));
    return http.build();
  }
}
```

4. **User Endpoint** (UserController.java):
```java
@RestController
@RequestMapping("/api")
public class UserController {
  @GetMapping("/user")
  public OAuth2User getUser(@AuthenticationPrincipal OAuth2User user) {
    if (user == null) {
      throw new UnauthorizedException("Not authenticated");
    }
    // Return user data from GitHub
    return user;
  }
  
  @PostMapping("/logout")
  public void logout(HttpServletRequest request, HttpServletResponse response) {
    SecurityContextHolder.clearContext();
    // Your logout logic
  }
}
```

### Phase 3: Update Frontend Code (1-2 hours)

**Remove OAuth logic from frontend:**

```javascript
// NEW: Simple backend calls
export const authService = {
  // Login just redirects to backend endpoint
  login: () => {
    window.location.href = '/oauth2/authorization/github';
  },
  
  // Get current user from backend
  getUser: async () => {
    const response = await fetch('/api/user', {
      credentials: 'include'  // Important: send cookies!
    });
    if (response.status === 401) {
      window.location.href = '/';  // Redirect to login
      return null;
    }
    return response.json();
  },
  
  // Logout
  logout: async () => {
    await fetch('/api/logout', {
      method: 'POST',
      credentials: 'include'
    });
    window.location.href = '/';
  }
};
```

**Update API calls:**
```javascript
// BEFORE: Add token to every request
const headers = {
  'Authorization': `Bearer ${localStorage.getItem('github_token')}`,
};
fetch('/api/data', { headers });

// AFTER: Automatic via cookies + BFF
fetch('/api/data', {
  credentials: 'include'  // That's it!
});
```

### Phase 4: Setup nginx Reverse Proxy (1 hour)

**This is what makes "same-site" work:**

```nginx
server {
  listen 3000;
  
  # Serve frontend static files
  location / {
    root /var/www/frontend;
    try_files $uri /index.html;
  }
  
  # Proxy backend calls
  location /api/ {
    proxy_pass http://backend:8080/api/;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $server_name;
  }
  
  # Proxy OAuth endpoints
  location /oauth2/ {
    proxy_pass http://backend:8080/oauth2/;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $server_name;
  }
}
```

### Phase 5: Docker Everything (1 hour)

**docker-compose.yml:**
```yaml
version: '3.8'

services:
  nginx:
    image: nginx:alpine
    ports:
      - "${PORT:-3000}:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
      - ./frontend/build:/var/www/frontend:ro
  
  backend:
    build: ./backend
    environment:
      - GITHUB_CLIENT_ID=${GITHUB_CLIENT_ID}
      - GITHUB_CLIENT_SECRET=${GITHUB_CLIENT_SECRET}
```

---

## Migration Checklist

### Code Changes
- [ ] Remove all `localStorage.getItem('token')` calls
- [ ] Remove all `localStorage.setItem('token')` calls
- [ ] Remove token refresh logic from frontend
- [ ] Update API calls to use `credentials: 'include'`
- [ ] Remove GitHub OAuth initialization from frontend
- [ ] Update login button to just POST/redirect to `/oauth2/authorization/github`

### Backend Setup
- [ ] Add Spring Security OAuth2 dependencies
- [ ] Create SecurityConfig with `oauth2Login()`
- [ ] Create `/api/user` endpoint
- [ ] Create `/api/logout` endpoint
- [ ] Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET
- [ ] Configure session timeout (for HIPAA)
- [ ] Setup security headers (HSTS, CSP, etc.)

### Deployment
- [ ] Create nginx.conf
- [ ] Create Dockerfile for backend
- [ ] Create Dockerfile for frontend
- [ ] Create docker-compose.yml
- [ ] Update GitHub OAuth app callback URL to new domain:port
- [ ] Test with `PORT=9000` to verify port-agnostic setup

### Testing
- [ ] Login works
- [ ] User data displays correctly
- [ ] Token/session persists on page reload
- [ ] Logout clears session
- [ ] Expired session redirects to login
- [ ] CSRF protection working
- [ ] Works at multiple ports (localhost:3000, localhost:9000)

---

## Key Benefits After Migration

| Aspect | Before (Frontend OAuth) | After (BFF) |
|--------|------------------------|------------|
| **Token Storage** | localStorage (XSS risk) | HttpOnly cookie (secure) |
| **CORS Config** | Complex allow-list | Zero CORS needed |
| **Session Timeout** | Manual frontend logic | Server-enforced |
| **HIPAA Log Reading** | Access token with PII | Session ID only |
| **Deployment Flexibility** | Tied to frontend | Independent backend |
| **Auth Logic Updates** | Requires frontend redeploy | Backend only |
| **Development** | CORS proxy needed | Single localhost:3000 |
| **Production** | Multiple domains | Single domain |

---

## Common Pitfalls to Avoid

1. **Forget `credentials: 'include'` on API calls**
   ```javascript
   // ❌ WRONG: Cookie won't be sent
   fetch('/api/data');
   
   // ✅ RIGHT
   fetch('/api/data', { credentials: 'include' });
   ```

2. **Forgot to update GitHub OAuth callback URL**
   - Update GitHub app settings to new domain/port
   - Must match exactly what Spring redirect generates

3. **Session cookie marked HttpOnly=true**
   - Frontend JS can't read it (correct!)
   - But means "credentials: 'include'" must be used on all API calls

4. **Hardcoded ports in configuration**
   - Use environment variables with defaults
   - e.g., `PORT=${PORT:-3000}` in docker-compose

5. **Not proxying /oauth2/ and /login/ endpoints**
   - Frontend calls `/oauth2/authorization/github`
   - nginx must proxy this to backend (not to github.com!)

---

## Reference: This Demo Already Shows All This

This project (`oauth-bff`) is the complete reference implementation. You can:

1. Copy the entire backend configuration from `server/src/main/java/com/example/server/SecurityConfig.java`
2. Use the React integration from `client/src/services/auth.js`
3. Use the docker-compose.yml and nginx.conf as templates
4. Modify as needed for your specific requirements

---

## Questions to Ask Your Team

1. **Do we have HIPAA/security requirements?**
   - If yes: BFF is **required** (frontend OAuth won't pass audit)

2. **Are tokens currently exposed in localStorage?**
   - If yes: BFF fixes this immediately

3. **How many frontend apps share the same backend?**
   - More apps = bigger BFF benefit (centralized Auth logic)

4. **Can we afford frontend deploys just for auth changes?**
   - No = BFF lets you update backend independently

---

## Timeline for Medium Refactor

- **Discovery:** 4-6 hours
- **Backend setup:** 2-3 days
- **Frontend updates:** 1-2 days
- **Docker/deployment:** 1 day
- **Testing:** 1-2 days
- **Total: 1 week** of concentrated work

Alternatively, small incremental changes over sprints = less risk

---

## Get Help

1. **This demo:** Run it, understand the flow, adapt
2. **Spring Security docs:** https://spring.io/projects/spring-security
3. **OAuth2 standard:** https://tools.ietf.org/html/rfc6749
4. **Your security/compliance team:** Start conversations early
