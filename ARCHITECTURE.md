# Architecture & Request Flow Guide

## Complete Request Flow: What Happens When User Clicks "Login"

### Step 1: User Clicks "Login with GitHub"

**Frontend Code** (`client/src/services/auth.js`):
```javascript
export const authService = {
  login: () => {
    window.location.href = '/oauth2/authorization/github';
  }
};
```

**What happens:** Browser navigates to `/oauth2/authorization/github`

---

### Step 2: nginx Receives Request on Port 9000

**Browser sends:**
```
GET /oauth2/authorization/github HTTP/1.1
Host: localhost:9000
```

**nginx.conf** (`location /oauth2/`):
```nginx
location /oauth2/ {
    proxy_pass http://backend:8080/oauth2/;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $server_name;
    proxy_set_header X-Forwarded-Port ${PORT};  # ← CRUCIAL: Tells backend what external port was used
}
```

**nginx does:** Routes to backend:8080, BUT includes headers telling backend "I came from localhost:9000"

---

### Step 3: Spring Boot OAuth Handler

**Spring Security intercepts** the `/oauth2/authorization/github` request

**SecurityConfig.java**:
```java
.oauth2Login(oauth -> oauth
    .defaultSuccessUrl("/dashboard", true))  // After OAuth succeeds, redirect here
```

**What Spring does:**
1. Generates OAuth request to GitHub
2. **Constructs the redirect_uri** using X-Forwarded headers:
   ```
   redirect_uri = https://localhost:9000/login/oauth2/code/github
   ```
3. Redirects browser to GitHub:
   ```
   https://github.com/login/oauth/authorize?
     client_id=abc123&
     redirect_uri=https://localhost:9000/login/oauth2/code/github&
     scope=user,repo
   ```

**Why this works with any port:**
- nginx passes `X-Forwarded-Port: 9000`
- Spring reads it via `port-header: X-Forwarded-Port`
- Constructs redirect_uri with correct port
- **No hardcoded ports needed!**

---

### Step 4: User Authorizes on GitHub

Browser displays GitHub's permission screen. User clicks "Authorize".

---

### Step 5: GitHub Redirects Back

**GitHub redirects to:**
```
https://localhost:9000/login/oauth2/code/github?code=abc123&state=xyz
```

**Browser sends:**
```
GET /login/oauth2/code/github?code=abc123&state=xyz HTTP/1.1
Host: localhost:9000
```

**nginx routes this to backend** (same `/login/` block in nginx.conf)

---

### Step 6: Spring Processes OAuth Code

**Spring Security** (automatically):
1. Receives authorization `code` from GitHub
2. **Securely exchanges code for access token** (backend-to-backend, not in browser!)
3. Fetches user data from GitHub API (using access token)
4. **Creates a session** (no token stored in browser!)
5. Stores session in memory/Redis
6. **Sets session cookie**: `JSESSIONID=abc123xyz; Path=/; HttpOnly; SameSite=Lax`
7. Redirects to `/dashboard`

---

### Step 7: User Sees Dashboard

Browser is redirected to `/dashboard`, already authenticated because:
- Session cookie (`JSESSIONID`) is set
- Cookie automatically sent with every request to same domain
- Backend verifies session, knows user is authenticated

```javascript
// Frontend just calls:
const user = await fetch('/api/user', { credentials: 'include' }).then(r => r.json());
// Spring verifies session cookie, returns user data
```

---

## Why This Is Secure (HIPAA Compliant)

### 1. Access Token Never Reaches Browser
```
Frontend OAuth (WRONG):
  GitHub → access_token → Browser localStorage → JavaScript can leak it

BFF (CORRECT):
  GitHub → access_token → Backend only → Session cookie to browser
  Access token never exposed in browser!
```

### 2. Session Timeout Enforced
```yaml
spring:
  session:
    timeout: 15m  # Server kicks you out after 15 min, can't be changed by user
```

### 3. CSRF Protection Built In
```java
// Spring Security automatically prevents CSRF attacks
.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
```

### 4. Secure Cookies
```yaml
server:
  servlet:
    session:
      cookie:
        http-only: true        # JavaScript can't read it (XSS protection)
        secure: true           # Only sent over HTTPS (production)
        same-site: lax         # Only sent to same domain
```

---

## How Different Ports Work

### Scenario: Running on Port 9000

```
docker-compose command:
$ PORT=9000 docker compose up --build

What happens:
1. nginx container listens on port 9000 (from docker-compose.yml: ports: ["${PORT:-3000}:80"])
2. Browser user visits: http://localhost:9000
3. nginx container gets X-Forwarded-Port inside environment
4. entrypoint.sh does: envsubst '${PORT}' < nginx.conf.template > nginx.conf
5. nginx.conf now has: proxy_set_header X-Forwarded-Port 9000;
6. Spring reads X-Forwarded-Port and uses 9000 for OAuth redirect_uri
7. GitHub redirect goes to: http://localhost:9000/login/oauth2/code/github ✅
```

### What Happens Without Port Forwarding

```
If we removed X-Forwarded-Port:
1. Browser requests: http://localhost:9000/oauth2/authorization/github
2. Spring sees request but doesn't know external port
3. Spring defaults to port 80
4. Constructs redirect_uri: http://localhost:80/login/oauth2/code/github ❌
5. GitHub redirects to port 80 (nginx not listening there)
6. "Unable to connect" error
```

---

## Request Authentication Flow

### Authenticated Request from Frontend

```javascript
// Frontend makes API call
fetch('/api/user', { 
  credentials: 'include'  // ← CRITICAL
});
```

**Browser sends:**
```
GET /api/user HTTP/1.1
Host: localhost:9000
Cookie: JSESSIONID=abc123xyz; XSRF-TOKEN=def456
```

**nginx routes to backend:**
```
proxy_set_header Host $host;  // Keeps original Host header
// ... other X-Forwarded headers ...
```

**Spring Backend:**
```java
@GetMapping("/api/user")
public OAuth2User getUser(@AuthenticationPrincipal OAuth2User user) {
    // Spring verifies session cookie
    // @AuthenticationPrincipal gives us the logged-in user
    return user;
}
```

**Response:**
```json
{
  "login": "username",
  "name": "Full Name",
  "avatar_url": "https://...",
  "email": "user@example.com"
}
```

---

## CSRF Protection: How It Works

### The Problem
```
Hacker's website makes request to your site:
<img src="https://your-app.com/api/delete-account" />

Without CSRF protection: Your backend doesn't know it's an attack
```

### The Solution: CSRF Tokens

**Frontend receives token:**
```javascript
// Browser already has XSRF-TOKEN cookie (set by Spring)
const token = document.cookie
  .split('; ')
  .find(row => row.startsWith('XSRF-TOKEN='))
  .split('=')[1];
```

**Frontend sends token with request:**
```javascript
fetch('/api/sensitive', {
  method: 'POST',
  headers: {
    'X-XSRF-TOKEN': token,  // ← Must match cookie
  },
  credentials: 'include'
});
```

**Backend verifies:**
```
1. Token in cookie matches X-XSRF-TOKEN header? ✅
2. Request came from same-site? ✅
3. Allow request
```

**Hacker's website:**
```
<img src="..."> can't read cookie (HttpOnly? No, but same-site blocks it)
Can't set custom headers (browser prevents it)
Attack fails! ✅
```

---

## Testing the Flow

### Test 1: Login Works

```bash
# Start app
PORT=9000 docker compose up --build -d

# Open browser
open http://localhost:9000

# Click "Login with GitHub"
# Should redirect to GitHub, then back, then show dashboard
```

### Test 2: Session Persists

```bash
# After login, check cookies
DevTools → Application → Cookies → http://localhost:9000
# Should see:
# - JSESSIONID (session ID)
# - XSRF-TOKEN (CSRF protection)
```

### Test 3: Authentication Required

```bash
# Get session ID from cookie, paste into curl

curl -b "JSESSIONID=xxx" http://localhost:9000/api/user
# Should return user data if valid session

curl -b "JSESSIONID=invalid" http://localhost:9000/api/user
# Should return 401 Unauthorized
```

### Test 4: Works on Any Port

```bash
docker compose down

PORT=8000 docker compose up --build -d
open http://localhost:8000
# Should work fine!

docker compose down

PORT=9000 docker compose up --build -d
open http://localhost:9000
# Should also work!
```

---

## Adding More Endpoints

### Add a Protected API Endpoint

**Backend** (backend/src/main/java/UserDataController.java):
```java
@RestController
@RequestMapping("/api")
public class UserDataController {
    @GetMapping("/repos")
    public List<String> getUserRepos(
        @AuthenticationPrincipal OAuth2User user,
        RestTemplate restTemplate  // Inject HTTP client
    ) {
        // Only works if user is authenticated (session valid)
        String username = user.getAttribute("login");
        
        // Fetch user's repos from GitHub API
        String url = "https://api.github.com/users/" + username + "/repos";
        // ... fetch and return ...
    }
}
```

**Frontend** (client/src/services/api.js):
```javascript
export async function getUserRepos() {
  const response = await fetch('/api/repos', { 
    credentials: 'include' 
  });
  // Session cookie automatically sent!
  return response.json();
}
```

That's it! Because authentication is server-side:
- No token management needed
- No refresh logic needed
- No XSS token leakage possible

---

## Debugging Checklist

**Symptom: "Unable to connect" on GitHub redirect**
- [ ] Check `PORT` environment variable is set correctly
- [ ] Verify nginx X-Forwarded-Port matches PORT
- [ ] Check Spring's `port-header: X-Forwarded-Port` is configured
- [ ] Run `envsubst` manually to see generated nginx config

**Symptom: Login works, but "/api/user" returns 401**
- [ ] Check browser sends `credentials: 'include'` on fetch
- [ ] Check cookie is present in DevTools
- [ ] Verify Spring session timeout hasn't expired

**Symptom: Errors when running on different port**
- [ ] Make sure PORT env var is set BEFORE docker-compose
- [ ] Run `docker compose down` then `docker compose up` (rebuild)
- [ ] Check GitHub OAuth app callback URL matches

**Symptom: CSRF errors on POST requests**
- [ ] Frontend must send `X-XSRF-TOKEN` header
- [ ] Token must match cookie value
- [ ] Check `SpaCsrfTokenRequestHandler` is registered in SecurityConfig
