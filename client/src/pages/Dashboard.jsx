import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import './Dashboard.css';

export const Dashboard = () => {
  const { user, loading, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (!loading && !isAuthenticated) {
      navigate('/');
    }
  }, [loading, isAuthenticated, navigate]);

  if (loading) {
    return (
      <div className="page">
        <div className="loading">Loading...</div>
      </div>
    );
  }

  if (!user || user.error) {
    return null;
  }

  return (
    <div className="page">
      <div className="dashboard">
        <h1>Dashboard</h1>
        
        <div className="profile">
          <div className="profile-header">
            <img 
              src={user.avatar_url} 
              alt={user.login}
              className="avatar"
            />
            <div className="profile-info">
              <h2>{user.name || user.login}</h2>
              <p className="username">@{user.login}</p>
              {user.email && <p className="email">{user.email}</p>}
            </div>
          </div>
          
          {user.bio && (
            <div className="bio">
              <p>{user.bio}</p>
            </div>
          )}

          <div className="details">
            {user.company && (
              <div className="detail-item">
                <strong>Company:</strong> {user.company}
              </div>
            )}
            {user.location && (
              <div className="detail-item">
                <strong>Location:</strong> {user.location}
              </div>
            )}
            {user.blog && (
              <div className="detail-item">
                <strong>Website:</strong> <a href={user.blog} target="_blank" rel="noopener noreferrer">{user.blog}</a>
              </div>
            )}
          </div>

          <div className="stats">
            <div className="stat">
              <strong>{user.public_repos}</strong>
              <span>Repositories</span>
            </div>
            <div className="stat">
              <strong>{user.followers}</strong>
              <span>Followers</span>
            </div>
            <div className="stat">
              <strong>{user.following}</strong>
              <span>Following</span>
            </div>
          </div>

          <div className="actions">
            <a 
              href={user.html_url} 
              target="_blank" 
              rel="noopener noreferrer"
              className="button"
            >
              View GitHub Profile
            </a>
            <button onClick={logout} className="button button-secondary">
              Logout
            </button>
          </div>
        </div>

        <details className="debug">
          <summary>Debug: Raw User Data</summary>
          <pre>{JSON.stringify(user, null, 2)}</pre>
        </details>

        <div className="info-box">
          <h3>🔒 Security Features Active</h3>
          <ul>
            <li>✅ Session-based authentication (JSESSIONID cookie)</li>
            <li>✅ CSRF protection (XSRF-TOKEN cookie + header)</li>
            <li>✅ Same-site cookies (no CORS needed)</li>
            <li>✅ HttpOnly session cookie (XSS protection)</li>
            <li>✅ 15-minute session timeout (HIPAA compliant)</li>
          </ul>
          <p className="check-cookies">
            Check DevTools → Application → Cookies to see: JSESSIONID, XSRF-TOKEN
          </p>
        </div>
      </div>
    </div>
  );
};
