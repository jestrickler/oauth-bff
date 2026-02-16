package com.example.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * ==========================================
 * SPRING SECURITY CONFIGURATION
 * Backend-for-Frontend (BFF) Pattern
 * ==========================================
 * 
 * This configuration implements the BFF pattern for a Single Page Application (SPA).
 * The backend acts as a secure gateway between the frontend and OAuth provider (GitHub).
 * 
 * KEY SECURITY CONCEPTS:
 * 
 * 1. SESSION-BASED AUTHENTICATION
 *    - User authenticates via OAuth2 (GitHub)
 *    - Session stored server-side with JSESSIONID cookie
 *    - Cookie is HttpOnly (prevents XSS from stealing session)
 *    - Cookie is SameSite=Lax (prevents CSRF attacks)
 * 
 * 2. CSRF PROTECTION
 *    - Protects state-changing operations (POST, PUT, DELETE)
 *    - XSRF-TOKEN cookie sent to browser (HttpOnly=false so React can read it)
 *    - React must send token in X-XSRF-TOKEN header
 *    - GET requests don't need CSRF (read-only, CORS protects response)
 * 
 * 3. CORS (Cross-Origin Resource Sharing)
 *    - Development: Frontend (:5173) and backend (:8080) are different origins
 *    - CORS allows frontend to read API responses
 *    - allowCredentials=true allows cookies to be sent
 *    - Production: Deploy on same domain via reverse proxy (no CORS needed)
 * 
 * 4. SECURITY HEADERS
 *    - Content-Security-Policy: Prevents XSS attacks
 *    - HSTS: Forces HTTPS (production only)
 *    - X-Frame-Options: Prevents clickjacking
 * 
 * HIPAA CONSIDERATIONS:
 * - Session timeout: 15 minutes (configurable)
 * - HTTPS required in production
 * - Audit logging should be added for PHI access
 * - Consider MFA for additional security
 * 
 * @author Your Team
 * @version 1.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * Main security configuration using Spring Security's SecurityFilterChain.
     * This replaces the deprecated WebSecurityConfigurerAdapter.
     * 
     * AUTHENTICATION FLOW:
     * 1. User clicks "Login with GitHub"
     * 2. Redirected to /oauth2/authorization/github
     * 3. Spring Security redirects to GitHub OAuth page
     * 4. User authorizes the app on GitHub
     * 5. GitHub redirects back with authorization code
     * 6. Spring Security exchanges code for access token
     * 7. User profile loaded from GitHub
     * 8. Session created, user redirected to frontend dashboard
     * 
     * AUTHORIZATION:
     * - Public endpoints: /, /error, /login/**, /oauth2/**
     * - Protected: Everything else requires authentication
     * 
     * @param http HttpSecurity builder
     * @return SecurityFilterChain configured security filter chain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ==========================================
            // CORS Configuration
            // ==========================================
            // Allows frontend on different origin to access this API
            // In production, use reverse proxy for same-origin (more secure)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // ==========================================
            // URL Authorization Rules
            // ==========================================
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - no authentication required
                .requestMatchers("/", "/error", "/login/**", "/oauth2/**").permitAll()
                
                // All other endpoints require authentication
                // This includes /api/user, /api/logout, etc.
                .anyRequest().authenticated()
            )
            
            // ==========================================
            // OAuth2 Login Configuration
            // ==========================================
            .oauth2Login(oauth -> oauth
                // After successful GitHub authentication, redirect to frontend
                // Use relative URL - works on any domain/port
                // 'true' forces redirect even if user was on a different page
                .defaultSuccessUrl("/dashboard", true)
                
                // Optional: Customize login page
                // .loginPage("/custom-login")
                
                // Optional: Customize authorization endpoint
                // .authorizationEndpoint(auth -> auth.baseUri("/oauth2/authorize"))
            )
            
            // ==========================================
            // Logout Configuration
            // ==========================================
            .logout(logout -> logout
                // Logout endpoint (requires POST for CSRF protection)
                .logoutUrl("/api/logout")
                
                // Redirect after logout - use relative URL
                .logoutSuccessUrl("/")
                
                // Invalidate server-side session
                .invalidateHttpSession(true)
                
                // Delete authentication cookies
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                
                // Optional: Add logout handler for custom cleanup
                // .addLogoutHandler((request, response, auth) -> {
                //     // Custom cleanup (e.g., audit log)
                // })
            )
            
            // ==========================================
            // CSRF Protection Configuration
            // ==========================================
            .csrf(csrf -> csrf
                // Store CSRF token in a cookie (not in session)
                // HttpOnly=false allows JavaScript to read it (required for SPA)
                // This is a security trade-off:
                // - PRO: Stateless CSRF protection
                // - CON: XSS can steal token (mitigated by CSP headers)
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                
                // SPA-specific CSRF handler
                // Handles both cookie-based (SPA) and form-based (server-rendered) tokens
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                
                // Optional: Disable CSRF for specific endpoints
                // .ignoringRequestMatchers("/api/webhook/**")
            )
            
            // Force CSRF token to be loaded on every request
            // This ensures the XSRF-TOKEN cookie is sent even on first request
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            
            // ==========================================
            // Session Management Configuration
            // ==========================================
            .sessionManagement(session -> session
                // Create session when needed (during authentication)
                // Options: ALWAYS, NEVER, IF_REQUIRED, STATELESS
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                
                // Prevent session fixation attacks
                // Creates new session after authentication
                .sessionFixation().newSession()
                
                // Limit to one session per user
                // Prevents session hijacking and concurrent logins
                // For HIPAA: prevents sharing credentials
                .maximumSessions(1)
                    // false: New login kicks out old session (recommended)
                    // true: New login rejected if session exists
                    .maxSessionsPreventsLogin(false)
            )
            
            // ==========================================
            // Security Headers Configuration
            // ==========================================
            .headers(headers -> headers
                // Content Security Policy - Prevents XSS attacks
                // Only allow resources from same origin
                // Prevents inline scripts and eval()
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; frame-ancestors 'none';")
                )
                
                // HTTP Strict Transport Security (HSTS)
                // Forces HTTPS for all future requests
                // Only active when using HTTPS
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)  // 1 year
                )
                
                // Additional headers set by default:
                // - X-Content-Type-Options: nosniff
                // - X-Frame-Options: DENY
                // - X-XSS-Protection: 1; mode=block
            );
        
        return http.build();
    }

    /**
     * CORS Configuration Bean
     * 
     * UNDERSTANDING CORS:
     * - Browser security: Prevents website A from reading responses from website B
     * - Two websites are "different origins" if they differ in:
     *   - Scheme (http vs https)
     *   - Domain (example.com vs api.example.com)
     *   - Port (localhost:8080 vs localhost:5173)
     * 
     * DEVELOPMENT vs PRODUCTION:
     * 
     * Development:
     * - Frontend: http://localhost:5173 (Vite dev server)
     * - Backend: http://localhost:8080 (Spring Boot)
     * - Different origins → CORS required
     * 
     * Production (BFF Pattern - RECOMMENDED):
     * - Using reverse proxy (nginx/Apache/cloud):
     *   https://app.example.com/ → Frontend (static files)
     *   https://app.example.com/api/ → Backend (proxied to :8080)
     * - Same origin → No CORS needed (more secure)
     * 
     * Production (Separate Domains - Less Secure):
     * - Frontend: https://app.example.com
     * - Backend: https://api.example.com
     * - Different origins → CORS required
     * - Must use SameSite=None; Secure cookies (security risk)
     * 
     * @return CorsConfigurationSource CORS configuration
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allowed origins (frontends that can access this API)
        // Development: http://localhost:5173
        // Production: https://app.example.com OR empty if same-origin
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        
        // Allowed HTTP methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // Allowed headers
        // "*" allows all headers (including X-XSRF-TOKEN)
        // For stricter security, list specific headers:
        // Arrays.asList("Content-Type", "Authorization", "X-XSRF-TOKEN")
        configuration.setAllowedHeaders(Arrays.asList("*"));
        
        // Allow credentials (cookies, authorization headers)
        // REQUIRED: For session cookies and CSRF tokens to be sent
        // SECURITY NOTE: Cannot use allowedOrigins("*") when this is true
        configuration.setAllowCredentials(true);
        
        // Expose headers to JavaScript
        // By default, only simple response headers are exposed
        // Add custom headers if frontend needs to read them
        // configuration.setExposedHeaders(Arrays.asList("X-Custom-Header"));
        
        // Apply configuration to all paths
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}