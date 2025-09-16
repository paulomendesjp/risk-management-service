package com.interview.challenge.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Configuration
public class RateLimitConfig {

    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        // Rate limiting based on authenticated user or IP address
        return exchange -> {
            // Try to get user from JWT token first
            String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                // Extract username from JWT (simplified - in production, decode the JWT)
                return Mono.just(authorization.substring(7).split("\\.")[0]);
            }

            // Fallback to IP address
            String ip = Objects.requireNonNull(exchange.getRequest().getRemoteAddress())
                    .getAddress().getHostAddress();
            return Mono.just(ip);
        };
    }

    @Bean
    public KeyResolver apiKeyResolver() {
        // Rate limiting based on API key
        return exchange -> {
            String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
            return Mono.just(apiKey != null ? apiKey : "anonymous");
        };
    }

    @Bean
    public KeyResolver pathKeyResolver() {
        // Rate limiting based on request path
        return exchange -> Mono.just(exchange.getRequest().getPath().value());
    }

    @Bean
    public KeyResolver clientIdKeyResolver() {
        // Rate limiting based on client ID header
        return exchange -> {
            String clientId = exchange.getRequest().getHeaders().getFirst("X-Client-ID");
            if (clientId != null) {
                return Mono.just(clientId);
            }
            // Fallback to IP address
            String ip = Objects.requireNonNull(exchange.getRequest().getRemoteAddress())
                    .getAddress().getHostAddress();
            return Mono.just(ip);
        };
    }

    @Bean
    public RedisRateLimiter customRedisRateLimiter() {
        // Custom rate limiter with different limits for different client types
        return new RedisRateLimiter(100, 200, 1);
    }

    @Bean("premiumRateLimiter")
    public RedisRateLimiter premiumRateLimiter() {
        // Higher limits for premium clients
        return new RedisRateLimiter(500, 1000, 1);
    }

    @Bean("basicRateLimiter")
    public RedisRateLimiter basicRateLimiter() {
        // Lower limits for basic clients
        return new RedisRateLimiter(50, 100, 1);
    }
}