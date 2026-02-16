/**
 * ==========================================
 * AUTHENTICATION HOOK
 * ==========================================
 * 
 * Provides authentication state and operations to components
 * 
 * USAGE:
 * const { user, loading, isAuthenticated, login, logout } = useAuth();
 */

import { useState, useEffect } from 'react';
import { authService } from '../services/auth';

export const useAuth = () => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    checkAuth();
  }, []);

  const checkAuth = async () => {
    try {
      setLoading(true);
      const userData = await authService.getUser();
      setUser(userData);
      setError(null);
    } catch (err) {
      setError(err.message);
      setUser(null);
    } finally {
      setLoading(false);
    }
  };

  const login = () => {
    authService.login();
  };

  const logout = async () => {
    try {
      await authService.logout();
      setUser(null);
    } catch (err) {
      setError(err.message);
    }
  };

  return {
    user,
    loading,
    error,
    isAuthenticated: !!user && !user.error,
    login,
    logout,
    refresh: checkAuth,
  };
};
