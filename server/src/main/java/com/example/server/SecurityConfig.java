package com.example.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * ==========================================
 * SPRING SECURITY CONFIGURATION
 * Backend-for-Frontend (BFF) Same-Site Pattern
 * ==========================================
 * 
 * Implements the BFF pattern for a Single Page Application (SPA).
 * Frontend and backend are behind a single reverse proxy (nginx),
 * appearing as same-origin to the browser.
 * 
 * ARCHITECTURE: Browser → nginx (port 3000) → Frontend (/) + Backend (/api/, /oauth2/)
 * 
 * KEY SECURITY CONCEPTS:
 * 
 * 1. SESSION-BASED AUTHENTICATION
 *    - User authenticates via OAuth2 (GitHub)
 *    - Session stored server-side with JSESSIONID cookie
 *    - HttpOnly flag prevents XSS from stealing the session
 *    - SameSite=Lax prevents CSRF attacks
 * 
 * 2. CSRF PROTECTION
 *    - Protects state-changing operations (POST, PUT, DELETE)
 *    - XSRF-TOKEN cookie sent to browser (HttpOnly=false for SPA access)
 *    - React sends token in X-XSRF-TOKEN header
 *    - GET requests don't need CSRF (read-only, same-site prevents attacks)
 * 
 * 3. NO CORS NEEDED
 *    - Frontend served from same origin via nginx reverse proxy
 *    - All API requests appear same-origin to browser
 *    - Cookies sent automatically with every request
 *    - More secure than separate domains
 * 
 * 4. SECURITY HEADERS
 *    - Content-Security-Policy: Prevents XSS attacks
 *    - HSTS: Forces HTTPS
 *    - X-Frame-Options: Prevents clickjacking
 * 
 * HIPAA CONSIDERATIONS:
 * - Session timeout: 15 minutes (configured in application-prod.yml)
 * - HTTPS required (configured in application-prod.yml)
 * - Audit logging should be added for PHI access
 * - Consider MFA for additional security
 * 
 * @author Your Team
 * @version 1.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {



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
            // CORS DISABLED - BFF Pattern
            // ==========================================
            // Frontend and backend served from same origin via nginx proxy
            // No CORS needed; all requests appear same-origin to browser
            // .cors() disabled
            
            // ==========================================
            // URL Authorization Rules
            // ==========================================
            .authorizeHttpRequests(auth -> auth
                // OAuth2 flow endpoints - must be public
                // /login/oauth2/code/github: GitHub callback (before session exists)
                // /oauth2/authorization/github: Start OAuth flow
                .requestMatchers("/login/**", "/oauth2/**").permitAll()
                
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

    // CORS DISABLED: BFF Pattern uses same-site (all requests appear same-origin)
    // If you need separate domains, enable CORS by:
    // 1. Uncommenting .cors(cors -> cors.configurationSource(corsConfigurationSource()))
    // 2. Creating corsConfigurationSource() Bean (see git history)
}