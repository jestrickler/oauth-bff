# Production Deployment Guide

Complete guide for deploying the OAuth BFF application to production.

## 🎯 Deployment Architecture

### Recommended: BFF Pattern with Reverse Proxy

```
                     ┌─────────────────────┐
                     │   Load Balancer     │
                     │  (AWS ALB / etc)    │
                     └──────────┬──────────┘
                                │ HTTPS (443)
                                │
                     ┌──────────▼──────────┐
                     │   Reverse Proxy     │
                     │   (nginx/Apache)    │
                     └──────────┬──────────┘
                                │
                 ┌──────────────┴───────────────┐
                 │                              │
      ┌──────────▼──────────┐      ┌──────────▼──────────┐
      │  Static Files       │      │  Spring Boot        │
      │  (React build)      │      │  (Backend)          │
      │  Served by nginx    │      │  :8080 (internal)   │
      └─────────────────────┘      └─────────────────────┘
```

## 📋 Pre-Deployment Checklist

### 1. Code Preparation

- [ ] All code committed to Git
- [ ] Sensitive data in environment variables (not hardcoded)
- [ ] Production profile configured (`application-prod.yml`)
- [ ] Dependencies up to date
- [ ] Security audit completed

### 2. Infrastructure

- [ ] Domain name registered
- [ ] SSL certificate obtained (Let's Encrypt or commercial)
- [ ] Server/cloud instance provisioned
- [ ] Database/Redis for session storage
- [ ] Firewall configured
- [ ] Monitoring setup

### 3. OAuth Configuration

- [ ] Production GitHub OAuth App created
- [ ] Callback URL set to production domain
- [ ] Client ID and Secret securely stored

## 🚀 Deployment Steps

### Option A: Single Server with nginx (Recommended for Start)

#### 1. Server Setup (Ubuntu/Debian)

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Java 21
sudo apt install openjdk-21-jdk -y

# Install nginx
sudo apt install nginx -y

# Install certbot for Let's Encrypt
sudo apt install certbot python3-certbot-nginx -y

# Install Redis (for session storage)
sudo apt install redis-server -y
sudo systemctl enable redis-server
```

#### 2. Build Backend

```bash
# On your local machine
cd server
./gradlew clean build

# Copy JAR to server
scp build/libs/server-0.0.1-SNAPSHOT.jar user@server:/opt/oauth-bff/
```

#### 3. Build Frontend

```bash
# On your local machine
cd client
npm run build

# Copy build files to server
scp -r dist/* user@server:/var/www/oauth-bff/
```

#### 4. Configure Backend Service

Create `/etc/systemd/system/oauth-bff.service`:

```ini
[Unit]
Description=OAuth BFF Backend
After=syslog.target network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/oauth-bff
ExecStart=/usr/bin/java \
    -Xmx512m \
    -Xms256m \
    -Dspring.profiles.active=prod \
    -jar /opt/oauth-bff/server-0.0.1-SNAPSHOT.jar

Environment="GITHUB_CLIENT_ID=your_prod_client_id"
Environment="GITHUB_CLIENT_SECRET=your_prod_client_secret"
Environment="CORS_ALLOWED_ORIGINS=https://app.example.com"

# Security
PrivateTmp=true
NoNewPrivileges=true

# Logging
StandardOutput=journal
StandardError=journal
SyslogIdentifier=oauth-bff

# Restart
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

**Start the service**:
```bash
sudo systemctl daemon-reload
sudo systemctl enable oauth-bff
sudo systemctl start oauth-bff
sudo systemctl status oauth-bff
```

#### 5. Configure nginx

Create `/etc/nginx/sites-available/oauth-bff`:

```nginx
# Redirect HTTP to HTTPS
server {
    listen 80;
    listen [::]:80;
    server_name app.example.com;
    
    # Allow certbot challenges
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }
    
    # Redirect everything else to HTTPS
    location / {
        return 301 https://$server_name$request_uri;
    }
}

# HTTPS server
server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name app.example.com;
    
    # SSL configuration
    ssl_certificate /etc/letsencrypt/live/app.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/app.example.com/privkey.pem;
    
    # Modern SSL configuration
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers off;
    ssl_ciphers 'ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384';
    
    # OCSP Stapling
    ssl_stapling on;
    ssl_stapling_verify on;
    ssl_trusted_certificate /etc/letsencrypt/live/app.example.com/chain.pem;
    
    # Security headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "DENY" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    
    # Gzip compression
    gzip on;
    gzip_vary on;
    gzip_types text/plain text/css text/xml text/javascript application/javascript application/json;
    
    # Root directory for React build
    root /var/www/oauth-bff;
    index index.html;
    
    # Frontend - React SPA
    location / {
        try_files $uri $uri/ /index.html;
        
        # Cache static assets
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
        }
    }
    
    # Backend API - Proxy to Spring Boot
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_http_version 1.1;
        
        # Required headers
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $server_name;
        
        # Timeouts
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
        
        # Buffering
        proxy_buffering off;
        proxy_request_buffering off;
    }
    
    # OAuth endpoints
    location /oauth2/ {
        proxy_pass http://localhost:8080/oauth2/;
        proxy_http_version 1.1;
        
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $server_name;
    }
    
    location /login/ {
        proxy_pass http://localhost:8080/login/;
        proxy_http_version 1.1;
        
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $server_name;
    }
    
    # Health check endpoint
    location /health {
        proxy_pass http://localhost:8080/health;
        access_log off;
    }
    
    # Access and error logs
    access_log /var/log/nginx/oauth-bff-access.log;
    error_log /var/log/nginx/oauth-bff-error.log;
}
```

**Enable site and get SSL certificate**:

```bash
# Enable the site
sudo ln -s /etc/nginx/sites-available/oauth-bff /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx

# Get Let's Encrypt certificate
sudo certbot --nginx -d app.example.com

# Auto-renewal (certbot sets this up automatically)
sudo certbot renew --dry-run
```

#### 6. Configure Session Store (Redis)

Update backend to use Redis for sessions.

**Add dependency to `build.gradle`**:
```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.session:spring-session-data-redis'
}
```

**Update `application-prod.yml`**:
```yaml
spring:
  session:
    store-type: redis
    timeout: 15m
  redis:
    host: localhost
    port: 6379
    password: ${REDIS_PASSWORD:}
```

#### 7. Production GitHub OAuth App

1. Go to GitHub → Settings → Developer Settings → OAuth Apps
2. Create **NEW** OAuth App (separate from development)
3. Settings:
   - **Homepage URL**: `https://app.example.com`
   - **Callback URL**: `https://app.example.com/login/oauth2/code/github`
4. Save Client ID and Secret
5. Update environment variables in systemd service

### Option B: Cloud Deployment (AWS Example)

#### Architecture

```
Route 53 (DNS)
    ↓
CloudFront (CDN for frontend)
    ↓
Application Load Balancer
    ↓
ECS/Fargate (Backend containers)
    ↓
ElastiCache Redis (Sessions)
```

#### Components

**1. Frontend (S3 + CloudFront)**:
```bash
# Build
npm run build

# Upload to S3
aws s3 sync dist/ s3://your-bucket --delete

# Invalidate CloudFront cache
aws cloudfront create-invalidation \
    --distribution-id YOUR_DIST_ID \
    --paths "/*"
```

**2. Backend (ECS with Fargate)**:

`Dockerfile`:
```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY build/libs/server-0.0.1-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -jar app.jar" ]
```

**3. ElastiCache for Redis**:
- Create Redis cluster
- Update Spring Boot to use Redis endpoint
- Ensure security groups allow connection

**4. Application Load Balancer**:
- Target: ECS tasks on port 8080
- Health check: `/health`
- HTTPS listener with ACM certificate

**5. Environment Variables** (ECS Task Definition):
```json
{
  "environment": [
    { "name": "SPRING_PROFILES_ACTIVE", "value": "prod" },
    { "name": "GITHUB_CLIENT_ID", "valueFrom": "arn:aws:secretsmanager:..." },
    { "name": "GITHUB_CLIENT_SECRET", "valueFrom": "arn:aws:secretsmanager:..." },
    { "name": "SPRING_REDIS_HOST", "value": "your-redis.cache.amazonaws.com" }
  ]
}
```

## 🔐 Security Hardening

### 1. Environment Variables

**NEVER commit secrets to Git**. Use:

- Environment variables (systemd service)
- AWS Secrets Manager / Parameter Store
- HashiCorp Vault
- Azure Key Vault
- Google Secret Manager

### 2. Firewall Configuration

```bash
# UFW (Ubuntu firewall)
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp   # SSH
sudo ufw allow 80/tcp   # HTTP
sudo ufw allow 443/tcp  # HTTPS
sudo ufw enable
```

### 3. Fail2ban (Prevent brute force)

```bash
sudo apt install fail2ban
sudo systemctl enable fail2ban
```

### 4. Regular Updates

```bash
# Automate security updates
sudo apt install unattended-upgrades
sudo dpkg-reconfigure --priority=low unattended-upgrades
```

### 5. Monitoring & Logging

**Application Logs**:
```bash
# View logs
sudo journalctl -u oauth-bff -f

# or if using file logging
tail -f /var/log/oauth-bff/application.log
```

**nginx Logs**:
```bash
tail -f /var/log/nginx/oauth-bff-access.log
tail -f /var/log/nginx/oauth-bff-error.log
```

**Setup log rotation** (`/etc/logrotate.d/oauth-bff`):
```
/var/log/oauth-bff/*.log {
    daily
    rotate 14
    compress
    delaycompress
    missingok
    notifempty
    create 0640 www-data www-data
}
```

## 📊 Monitoring

### Health Checks

Add to Spring Boot (`HomeController.java`):
```java
@GetMapping("/health")
public Map<String, String> health() {
    return Map.of(
        "status", "UP",
        "timestamp", Instant.now().toString()
    );
}
```

### Metrics (Optional)

Add Spring Boot Actuator:
```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

Configure in `application-prod.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

Protect with authentication in `SecurityConfig.java`.

## 🔄 CI/CD Pipeline (GitHub Actions Example)

`.github/workflows/deploy.yml`:

```yaml
name: Deploy to Production

on:
  push:
    branches: [ main ]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    # Backend
    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Build backend
      run: |
        cd server
        ./gradlew clean build
    
    # Frontend
    - name: Setup Node.js
      uses: actions/setup-node@v3
      with:
        node-version: '18'
    
    - name: Build frontend
      run: |
        cd client
        npm ci
        npm run build
    
    # Deploy (example: SCP to server)
    - name: Deploy to server
      env:
        SSH_PRIVATE_KEY: ${{ secrets.SSH_PRIVATE_KEY }}
      run: |
        # Set up SSH
        mkdir -p ~/.ssh
        echo "$SSH_PRIVATE_KEY" > ~/.ssh/id_rsa
        chmod 600 ~/.ssh/id_rsa
        
        # Deploy backend
        scp server/build/libs/*.jar user@server:/opt/oauth-bff/
        
        # Deploy frontend
        scp -r client/dist/* user@server:/var/www/oauth-bff/
        
        # Restart service
        ssh user@server "sudo systemctl restart oauth-bff"
```

## 📋 Post-Deployment Checklist

- [ ] Application accessible via HTTPS
- [ ] HTTP redirects to HTTPS
- [ ] SSL certificate valid (check with SSL Labs)
- [ ] OAuth login flow works
- [ ] Logout works
- [ ] Session timeout works (wait 15 min)
- [ ] CSRF tokens working on POST requests
- [ ] Cookies have Secure flag
- [ ] Security headers present (check with securityheaders.com)
- [ ] Monitoring alerts configured
- [ ] Backup strategy in place
- [ ] Incident response plan documented

## 🆘 Troubleshooting

### Issue: OAuth redirect fails

**Symptoms**: After GitHub auth, redirected to wrong URL

**Solution**:
1. Check GitHub OAuth app callback URL matches production
2. Check `defaultSuccessUrl` in SecurityConfig
3. Verify `X-Forwarded-Proto` header is set in nginx

### Issue: Cookies not being set

**Symptoms**: Constant login prompts, 401 errors

**Solution**:
1. Check `secure: true` in production profile
2. Verify HTTPS is working
3. Check `SameSite` attribute
4. Inspect cookies in DevTools

### Issue: CSRF 403 errors

**Symptoms**: POST requests failing with 403

**Solution**:
1. Verify XSRF-TOKEN cookie is present
2. Check X-XSRF-TOKEN header is sent
3. Verify token matches between cookie and header
4. Check CORS configuration

### Issue: Session lost on restart

**Symptoms**: Users logged out when backend restarts

**Solution**:
- Implement Redis session store (not in-memory)
- Configure session persistence

## 📚 Additional Resources

- [nginx SSL Configuration Generator](https://ssl-config.mozilla.org/)
- [SSL Labs Server Test](https://www.ssllabs.com/ssltest/)
- [Security Headers Check](https://securityheaders.com/)
- [OWASP Deployment Guide](https://cheatsheetseries.owasp.org/cheatsheets/Deployment_Checklist.html)

---

**Production deployment complete! 🎉**

Remember to monitor logs and set up alerts for any issues.
