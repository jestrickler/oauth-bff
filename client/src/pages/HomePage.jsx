import { useAuth } from '../hooks/useAuth';
import './HomePage.css';

export const HomePage = () => {
  const { user, loading, login } = useAuth();

  if (loading) {
    return (
      <div className="page">
        <div className="loading">Loading...</div>
      </div>
    );
  }

  if (user && !user.error) {
    return (
      <div className="page">
        <div className="card">
          <h1>Welcome back, {user.name || user.login}!</h1>
          <p>You're already logged in.</p>
          <a href="/dashboard" className="button">
            Go to Dashboard →
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <div className="card">
        <h1>OAuth BFF Demo</h1>
        <p className="subtitle">Production-Ready Backend-for-Frontend Pattern</p>
        
        <div className="features">
          <h2>Features</h2>
          <ul>
            <li>✅ Same-site architecture (no CORS needed)</li>
            <li>✅ Session-based authentication</li>
            <li>✅ CSRF protection</li>
            <li>✅ Secure cookies (HttpOnly, SameSite)</li>
            <li>✅ GitHub OAuth integration</li>
            <li>✅ HIPAA-compliant configuration</li>
          </ul>
        </div>

        <div className="architecture">
          <h2>Architecture</h2>
          <pre>{`
┌─────────────┐
│   nginx     │ ← You are here (localhost:3000)
│   :3000     │
└──────┬──────┘
       │
┌──────┴────────────┐
│                   │
│  Frontend (/)     │  Backend (/api/*)
│  React SPA        │  Spring Boot
└───────────────────┘
Same Origin = Production-Ready
          `}</pre>
        </div>

        <button onClick={login} className="button button-primary">
          Login with GitHub
        </button>

        <div className="note">
          <strong>Note:</strong> This demo uses the exact same architecture as production.
          Only difference: Production uses HTTPS instead of HTTP.
        </div>
      </div>
    </div>
  );
};
