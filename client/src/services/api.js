/**
 * ==========================================
 * API CLIENT WITH CSRF TOKEN SUPPORT
 * ==========================================
 * 
 * PRODUCTION-READY API CLIENT
 * 
 * Handles:
 * - Session cookies (credentials: 'include')
 * - CSRF tokens (automatic via same-site cookies)
 * - Error handling  
 * - 401 redirects to login
 * 
 * IMPORTANT - SAME-SITE SETUP:
 * - Frontend: http://localhost:3000/
 * - Backend : http://localhost:3000/api/*
 * - Same origin = cookies work automatically
 * - No CORS needed (this is production setup)
 * 
 * CSRF TOKEN HANDLING:
 * - XSRF-TOKEN cookie sent automatically by backend (CsrfCookieFilter)
 * - We read it from document.cookie
 * - Send in X-XSRF-TOKEN header for POST/PUT/DELETE
 * - No /api/csrf endpoint needed!
 */

/**
 * Get CSRF token from cookie
 * 
 * WHY FROM COOKIE:
 * - Backend sends XSRF-TOKEN cookie on every request
 * - Cookie is HttpOnly=false so JavaScript can read it
 * - More secure than localStorage (not sent to other domains)
 * 
 * @returns {string|null} CSRF token value
 */
const getCsrfToken = () => {
  const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
};

/**
 * Base URL for API requests
 * 
 * SAME-SITE SETUP:
 * - Empty string = relative URLs
 * - Requests go to same origin (localhost:3000)
 * - nginx proxies /api/* to backend
 * 
 * This is EXACTLY how production works!
 */
const API_BASE_URL = '';

/**
 * Make API request with proper credentials and CSRF token
 * 
 * FEATURES:
 * - Automatically includes session cookies
 * - Adds CSRF token for state-changing methods
 * - Handles 401 by redirecting to login
 * - Returns parsed JSON
 * 
 * @param {string} endpoint - API endpoint (e.g., '/api/user')
 * @param {object} options - Fetch options
 * @returns {Promise<any>} Response data
 */
export const apiRequest = async (endpoint, options = {}) => {
  const url = `${API_BASE_URL}${endpoint}`;
  
  // Default configuration
  const config = {
    credentials: 'include', // REQUIRED: Send session cookies
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
    ...options,
  };
  
  // Add CSRF token for state-changing methods
  // GET requests don't need CSRF (read-only, CORS protects response)
  if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(options.method?.toUpperCase())) {
    const csrfToken = getCsrfToken();
    if (csrfToken) {
      config.headers['X-XSRF-TOKEN'] = csrfToken;
    } else {
      console.warn('CSRF token not found for state-changing request');
    }
  }
  
  try {
    const response = await fetch(url, config);
    
    // Handle 401 Unauthorized - redirect to login
    if (response.status === 401) {
      console.log('Not authenticated, redirecting to login...');
      window.location.href = '/oauth2/authorization/github';
      throw new Error('Not authenticated');
    }
    
    // Handle other errors
    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`HTTP ${response.status}: ${errorText || response.statusText}`);
    }
    
    // Parse JSON response
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      return await response.json();
    }
    
    return response;
  } catch (error) {
    console.error('API request failed:', error);
    throw error;
  }
};

/**
 * Convenience methods for common HTTP operations
 */
export const api = {
  /**
   * GET request (no CSRF required)
   */
  get: (endpoint) => apiRequest(endpoint, { method: 'GET' }),
  
  /**
   * POST request (CSRF required)
   */
  post: (endpoint, data) => apiRequest(endpoint, { 
    method: 'POST', 
    body: JSON.stringify(data) 
  }),
  
  /**
   * PUT request (CSRF required)
   */
  put: (endpoint, data) => apiRequest(endpoint, { 
    method: 'PUT', 
    body: JSON.stringify(data) 
  }),
  
  /**
   * DELETE request (CSRF required)
   */
  delete: (endpoint) => apiRequest(endpoint, { method: 'DELETE' }),
};
