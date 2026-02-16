package com.example.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ==========================================
 * CSRF COOKIE FILTER
 * ==========================================
 * 
 * This filter ensures the CSRF token is loaded and the cookie is sent on EVERY request.
 * 
 * WHY THIS IS NEEDED:
 * 
 * Spring Security 6+ uses "deferred token loading" for performance:
 * - CSRF token is only generated when explicitly accessed
 * - Cookie is only sent when token is loaded/accessed
 * - Problem: If token is never accessed, cookie is never sent
 * - SPAs need the cookie on first request to read the token
 * 
 * WHAT THIS FILTER DOES:
 * 
 * 1. Runs on every request (before business logic)
 * 2. Gets the CsrfToken from request attributes
 * 3. Calls getToken() to force deferred token to load
 * 4. Spring Security's CookieCsrfTokenRepository sees the token was accessed
 * 5. Repository sends the XSRF-TOKEN cookie in the response
 * 
 * FLOW WITHOUT THIS FILTER:
 * 
 * Request 1: GET /api/user
 *   → No CSRF cookie sent (token never accessed)
 *   → SPA: "Where's my CSRF token?!"
 * 
 * Request 2: POST /api/data (without CSRF token)
 *   → 403 Forbidden (missing CSRF token)
 * 
 * FLOW WITH THIS FILTER:
 * 
 * Request 1: GET /api/user
 *   → Filter forces token load
 *   → XSRF-TOKEN cookie sent in response
 *   → SPA can read cookie value
 * 
 * Request 2: POST /api/data (with X-XSRF-TOKEN header)
 *   → CSRF validated ✓
 *   → Request succeeds ✓
 * 
 * ALTERNATIVE APPROACHES:
 * 
 * 1. Dedicated /api/csrf endpoint (less convenient)
 *    - SPA must call this first to get token
 *    - Extra request before any POST/PUT/DELETE
 * 
 * 2. Access token in controller (inconsistent)
 *    - Each controller must remember to access CsrfToken
 *    - Easy to forget, hard to enforce
 * 
 * 3. This filter (recommended)
 *    - Automatic, consistent
 *    - Works for all endpoints
 *    - No extra SPA logic needed
 * 
 * PERFORMANCE NOTES:
 * - OncePerRequestFilter ensures this only runs once per request
 * - Token generation is cheap (UUID + timestamp)
 * - Cookie overhead is minimal (~40 bytes)
 * - Worth the convenience for SPAs
 * 
 * @see CookieCsrfTokenRepository
 * @see SpaCsrfTokenRequestHandler
 * @author Your Team
 */
public final class CsrfCookieFilter extends OncePerRequestFilter {

    /**
     * Processes each HTTP request to ensure CSRF token is loaded.
     * 
     * EXECUTION ORDER:
     * 1. Spring Security filter chain processes authentication
     * 2. CSRF token generated and stored in request attributes as "_csrf"
     * 3. This filter runs (added after BasicAuthenticationFilter)
     * 4. We access the token to trigger cookie generation
     * 5. Request continues to controller
     * 6. Response sent with XSRF-TOKEN cookie
     * 
     * @param request the HTTP request
     * @param response the HTTP response
     * @param filterChain the filter chain to continue processing
     * @throws ServletException if an error occurs during filtering
     * @throws IOException if an I/O error occurs during filtering
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        // Get the deferred CSRF token from request attributes
        // Spring Security's CsrfFilter has already put this here
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        
        // Force the deferred token to be loaded by accessing it
        // This triggers CookieCsrfTokenRepository to send the cookie
        // The token value is not used here, but calling getToken() is enough
        csrfToken.getToken();

        // Continue with the request
        // The CSRF cookie will be included in the response automatically
        filterChain.doFilter(request, response);
    }
}
