/**
 * ==========================================
 * AUTHENTICATION SERVICE
 * ==========================================
 * 
 * Handles all authentication operations:
 * - Get current user
 * - Login with GitHub OAuth
 * - Logout
 * 
 * PRODUCTION-READY IMPLEMENTATION
 */

import { api } from './api';

export const authService = {
  /**
   * Get current authenticated user
   * 
   * RETURNS: GitHub user data (username, avatar, email, etc.)
   * THROWS: If not authenticated (api.get handles 401 redirect)
   */
  getUser: async () => {
    try {
      return await api.get('/api/user');
    } catch (error) {
      console.error('Failed to get user:', error);
      return null;
    }
  },

  /**
   * Initiate login with GitHub OAuth
   * 
   * FLOW:
   * 1. Redirect to /oauth2/authorization/github
   * 2. Spring Security redirects to GitHub OAuth
   * 3. User authorizes
   * 4. GitHub redirects back to our app
   * 5. Spring Security creates session
   * 6. User redirected to /dashboard
   */
  login: () => {
    window.location.href = '/oauth2/authorization/github';
  },

  /**
   * Logout current user
   * 
   * FLOW:
   * 1. POST to /api/logout (requires CSRF token)
   * 2. Spring Security invalidates session
   * 3. Cookies deleted
   * 4. Redirect to home page
   */
  logout: async () => {
    try {
      await api.post('/api/logout');
      window.location.href = '/';
    } catch (error) {
      console.error('Logout failed:', error);
      // Force redirect anyway
      window.location.href = '/';
    }
  },
};
