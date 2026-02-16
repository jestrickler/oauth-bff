package com.example.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * ==========================================
 * SPA CSRF TOKEN REQUEST HANDLER
 * ==========================================
 * 
 * This class handles CSRF tokens for Single Page Applications (SPAs).
 * It supports both cookie-based and form-based CSRF token submission.
 * 
 * PROBLEM IT SOLVES:
 * 
 * Traditional web apps submit forms with CSRF tokens as hidden fields.
 * SPAs receive CSRF tokens in cookies and send them in headers.
 * This handler supports BOTH patterns in a single application.
 * 
 * HOW IT WORKS:
 * 
 * 1. Token Storage (via CookieCsrfTokenRepository):
 *    - Server generates CSRF token
 *    - Stored in XSRF-TOKEN cookie (HttpOnly=false, so JavaScript can read)
 *    - Cookie is automatically sent with every request
 * 
 * 2. Token Submission (this class):
 *    - SPA reads XSRF-TOKEN cookie value
 *    - SPA sends token in X-XSRF-TOKEN header (standard convention)
 *    - This handler validates the header value
 * 
 * 3. BREACH Attack Protection:
 *    - Uses XorCsrfTokenRequestAttributeHandler for responses
 *    - XOR cipher prevents BREACH compression attacks
 *    - Token value changes on each request but validates to same token
 * 
 * FRONTEND USAGE (React):
 * 
 * ```javascript
 * // 1. Read CSRF token from cookie
 * const getCsrfToken = () => {
 *   const cookie = document.cookie.split('; ')
 *     .find(row => row.startsWith('XSRF-TOKEN='));
 *   return cookie ? cookie.split('=')[1] : null;
 * };
 * 
 * // 2. Send in header with POST/PUT/DELETE
 * fetch('/api/data', {
 *   method: 'POST',
 *   headers: {
 *     'Content-Type': 'application/json',
 *     'X-XSRF-TOKEN': getCsrfToken()
 *   },
 *   credentials: 'include',  // Include cookies
 *   body: JSON.stringify(data)
 * });
 * ```
 * 
 * WHY NOT JUST USE DEFAULT HANDLER:
 * - Default handler only supports form parameters (_csrf)
 * - SPAs need to send token in header (can't access HttpOnly cookies)
 * - This handler supports both header and form parameter
 * 
 * SECURITY NOTES:
 * - CSRF token in cookie is NOT HttpOnly (must be readable by JavaScript)
 * - XSS vulnerability could steal the token
 * - Mitigated by Content-Security-Policy headers
 * - Still more secure than no CSRF protection
 * 
 * @see CookieCsrfTokenRepository
 * @see XorCsrfTokenRequestAttributeHandler
 * @author Spring Security Team (adapted for BFF pattern)
 */
public final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
    
    /**
     * Delegate handler for XOR encoding (BREACH protection)
     */
    private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

    /**
     * Handles the CSRF token for a request.
     * Always delegates to XorCsrfTokenRequestAttributeHandler to provide
     * BREACH protection when rendering the token in responses.
     * 
     * BREACH Attack:
     * - Compression + HTTPS + Time = vulnerability
     * - XOR encoding prevents compression from revealing secrets
     * - Each token value is unique but validates to same underlying token
     * 
     * @param request the HTTP request
     * @param response the HTTP response
     * @param csrfToken supplier for the CSRF token
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, 
                      Supplier<CsrfToken> csrfToken) {
        /*
         * Always use XorCsrfTokenRequestAttributeHandler to provide BREACH protection of
         * the CsrfToken when it is rendered in the response body.
         */
        this.delegate.handle(request, response, csrfToken);
    }

    /**
     * Resolves the CSRF token value from the request.
     * 
     * LOGIC:
     * 1. If token is in header (X-XSRF-TOKEN) → SPA pattern
     *    - Use parent class (CsrfTokenRequestAttributeHandler)
     *    - Returns the raw token value from cookie
     * 
     * 2. If token is in parameter (_csrf) → Form pattern
     *    - Use XorCsrfTokenRequestAttributeHandler
     *    - Decodes the XOR-encoded token from form
     * 
     * This dual approach allows:
     * - SPAs to use modern header-based approach
     * - Server-rendered forms to use traditional parameter approach
     * - Both in the same application
     * 
     * @param request the HTTP request
     * @param csrfToken the CSRF token
     * @return the resolved CSRF token value, or null if not found
     */
    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        /*
         * If the request contains a request header, use CsrfTokenRequestAttributeHandler
         * to resolve the CsrfToken. This applies when a single-page application includes
         * the header value automatically, which was obtained via a cookie containing the
         * raw CsrfToken.
         */
        if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
            return super.resolveCsrfTokenValue(request, csrfToken);
        }
        /*
         * In all other cases (e.g. if the request contains a request parameter), use
         * XorCsrfTokenRequestAttributeHandler to resolve the CsrfToken. This applies
         * when a server-side rendered form includes the _csrf request parameter as a
         * hidden input.
         */
        return this.delegate.resolveCsrfTokenValue(request, csrfToken);
    }
}
