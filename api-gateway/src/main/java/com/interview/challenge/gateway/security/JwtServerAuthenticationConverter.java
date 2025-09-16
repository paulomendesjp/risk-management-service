
package com.interview.challenge.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Slf4j
public class JwtServerAuthenticationConverter implements ServerAuthenticationConverter {

    @Value("${jwt.header:Authorization}")
    private String headerName;

    @Value("${jwt.prefix:Bearer }")
    private String tokenPrefix;

    @Value("#{'${security.ignored-paths}'.split(',')}")
    private List<String> ignoredPaths;

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();

        // Skip authentication for ignored paths
        for (String ignoredPath : ignoredPaths) {
            if (path.startsWith(ignoredPath.trim().replace("**", ""))) {
                return Mono.empty();
            }
        }

        String authorizationHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader == null || !authorizationHeader.startsWith(tokenPrefix)) {
            log.debug("No JWT token found in request headers for path: {}", path);
            return Mono.empty();
        }

        String token = authorizationHeader.substring(tokenPrefix.length()).trim();

        if (token.isEmpty()) {
            log.warn("Empty JWT token received");
            return Mono.empty();
        }

        log.debug("JWT token found for path: {}", path);

        // Create an authentication token with the JWT as credentials
        // The actual validation will be done by JwtAuthenticationManager
        Authentication auth = new UsernamePasswordAuthenticationToken(token, token);

        return Mono.just(auth);
    }
}
