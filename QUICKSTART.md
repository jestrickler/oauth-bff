# Quick Start Guide - OAuth BFF Demo

Get up and running in **2 minutes** with Docker.

## Prerequisites

- Docker & Docker Compose installed
- GitHub account
- That's it! (No Node.js, Java, or development tools needed)

## Step 1: GitHub OAuth App Setup

1. Visit: https://github.com/settings/developers
2. Click "New OAuth App"
3. Fill in:
   ```
   Application name: BFF Demo
   Homepage URL: http://localhost:3000
   Callback URL: http://localhost:3000/login/oauth2/code/github
   ```
4. Save the **Client ID** and **Client Secret**

## Step 2: Create .env File

Create `.env` in project root:

```bash
GITHUB_CLIENT_ID=your_client_id_here
GITHUB_CLIENT_SECRET=your_client_secret_here
```

Replace with your actual values from GitHub!

## Step 3: Start Everything

```bash
docker compose up --build
```

That's it! Everything runs in Docker:
- Frontend builds automatically
- Backend compiles automatically  
- nginx reverse proxy starts
- Database/services spin up

## Step 4: Open the App

**http://localhost:3000**

Click "Login with GitHub" → Authorize → Done!

## Run on Different Port

Want to run on port 9000 instead?

```bash
PORT=9000 docker compose up --build
```

Then open **http://localhost:9000**

Or add to `.env`:
```
PORT=9000
```

## Stopping

```bash
docker compose down

This creates `client/dist/` with production build.

## Step 5: Start Docker Compose

```bash
docker-compose up --build
```

Wait for:
```
bff-backend   | Started OauthServerApplication in X.XXX seconds
bff-nginx     | start worker processes
```

## Step 6: Test the App

1. Open: http://localhost:3000
2. Click "Login with GitHub"
3. Authorize the app
4. See your GitHub profile!

## Verify Security

Open DevTools → Application → Cookies → `http://localhost:3000`

You should see:
- `JSESSIONID` (HttpOnly: ✓, SameSite: Lax)
- `XSRF-TOKEN` (HttpOnly: ✗, SameSite: Lax)

## Stop the App

```bash
docker-compose down
```

## Next Steps

- **Test logout:** Click logout button → session cleared
- **Test timeout:** Wait 15 minutes → session expires
- **Check logs:** `docker-compose logs -f backend`
- **Make changes:** Edit files → `docker-compose up --build`

## Production Deployment

See [docs/DEPLOYMENT_GUIDE.md](server/docs/DEPLOYMENT_GUIDE.md) for:
- nginx + SSL setup
- AWS/cloud deployment
- Environment configuration
- Monitoring & logging

---

**That's it!** You now have a production-ready BFF running locally with same-site security.

For detailed understanding, read:
- [Main README.md](README.md)
- [Security Concepts](server/docs/SECURITY_CONCEPTS.md)
