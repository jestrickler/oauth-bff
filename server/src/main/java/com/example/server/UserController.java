package com.example.server;

import java.util.Collections;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns authenticated user profile.
 * 
 * CURRENT: OAuth2User (GitHub - does not support OIDC)
 * FOR OpenID CONNECT (Keycloak, Google, etc.): Replace OAuth2User with OidcUser
 * 
 * @GetMapping("/user")
 * public Map<String, Object> getUser(@AuthenticationPrincipal OidcUser principal) {
 *   if (principal == null) {
 *     return Collections.singletonMap("error", "Not authenticated");
 *   }
 *   return principal.getClaims(); // standardized OIDC claims
 * }
 */
@RestController
@RequestMapping("/api")
public class UserController {

    @GetMapping("/user")
    public Map<String, Object> getUser(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return Collections.singletonMap("error", "Not authenticated");
        }
        return principal.getAttributes();
    }
}