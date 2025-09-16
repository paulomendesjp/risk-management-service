
package com.interview.challenge.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = authentication.getCredentials().toString();

        try {
            Claims claims = validateAndParseToken(token);

            if (claims == null || claims.getSubject() == null) {
                return Mono.empty();
            }

            String username = claims.getSubject();
            List<String> roles = extractRoles(claims);

            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    username,
                    null,
                    authorities
            );

            // Add additional details from JWT claims
            authToken.setDetails(claims);

            log.debug("Successfully authenticated user: {}", username);
            return Mono.just(authToken);

        } catch (Exception e) {
            log.error("JWT authentication failed: {}", e.getMessage());
            return Mono.empty();
        }
    }

    private Claims validateAndParseToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // Check token expiration
            Date expiration = claims.getExpiration();
            if (expiration != null && expiration.before(new Date())) {
                log.warn("JWT token has expired");
                return null;
            }

            return claims;
        } catch (Exception e) {
            log.error("Failed to parse JWT token: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Claims claims) {
        List<String> roles = new ArrayList<>();

        // Try to extract roles from different possible claim names
        Object rolesObj = claims.get("roles");
        if (rolesObj == null) {
            rolesObj = claims.get("authorities");
        }
        if (rolesObj == null) {
            rolesObj = claims.get("role");
        }

        if (rolesObj instanceof List) {
            roles = (List<String>) rolesObj;
        } else if (rolesObj instanceof String) {
            roles.add((String) rolesObj);
        }

        // If no roles found, assign default role
        if (roles.isEmpty()) {
            roles.add("USER");
        }

        return roles;
    }
}
