package com.example.server;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ==========================================
 * API ROOT ENDPOINT
 * ==========================================
 * 
 * Provides API discovery information.
 * Useful for health checks and exploring available endpoints.
 * 
 * NOTE ON CSRF TOKEN ENDPOINT:
 * We do NOT need a /api/csrf endpoint because:
 * - CsrfCookieFilter automatically sends XSRF-TOKEN cookie on EVERY request
 * - Frontend just reads document.cookie to get the token value
 * - No explicit API call needed
 * - This is the production-standard approach
 * 
 * If you need to debug CSRF tokens, check browser DevTools → Application → Cookies
 * 
 * @author Your Team
 * @version 1.0
 */
@RestController
public class HomeController {

    /**
     * Root endpoint - API information and health check.
     * 
     * Public endpoint (no authentication required).
     * 
     * PRODUCTION USE:
     * - Health check for load balancers
     * - API discovery for developers
     * - Version information
     * 
     * @return Map with API info and available endpoints
     */
    @GetMapping("/")
    public Map<String, String> home() {
        Map<String, String> response = new HashMap<>();
        response.put("service", "OAuth BFF Server");
        response.put("version", "1.0.0");
        response.put("status", "UP");
        
        // Available endpoints
        response.put("login", "GET /oauth2/authorization/github");
        response.put("user", "GET /api/user (authenticated)");
        response.put("logout", "POST /api/logout (authenticated)");
        
        return response;
    }
}
