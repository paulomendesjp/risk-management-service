package com.interview.challenge.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

/**
 * API Gateway Application for Trading Risk Management System
 * 
 * This gateway provides:
 * - Centralized entry point for all microservices
 * - Authentication and authorization
 * - Rate limiting and security
 * - Load balancing and circuit breaker
 * - Monitoring and logging
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    /**
     * Configure routes to microservices
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                
                // User Management Service Routes
                .route("user-service", r -> r
                    .path("/api/users/**")
                    .filters(f -> f
                        .stripPrefix(0)
                        .addRequestHeader("X-Gateway", "api-gateway")
                        .circuitBreaker(config -> config
                            .setName("user-service-cb")
                            .setFallbackUri("forward:/fallback/user-service")
                        )
                    )
                    .uri("${services.user-service.url:http://user-service:8080}")
                )
                
                // Risk Monitoring Service Routes
                .route("risk-monitoring-service", r -> r
                    .path("/api/risk/**")
                    .filters(f -> f
                        .stripPrefix(0)
                        .addRequestHeader("X-Gateway", "api-gateway")
                        .circuitBreaker(config -> config
                            .setName("risk-monitoring-cb")
                            .setFallbackUri("forward:/fallback/risk-monitoring")
                        )
                    )
                    .uri("${services.risk-monitoring.url:http://risk-monitoring-service:8080}")
                )
                
                // Position Management Service Routes
                .route("position-service", r -> r
                    .path("/api/positions/**")
                    .filters(f -> f
                        .stripPrefix(0)
                        .addRequestHeader("X-Gateway", "api-gateway")
                        .circuitBreaker(config -> config
                            .setName("position-service-cb")
                            .setFallbackUri("forward:/fallback/position-service")
                        )
                    )
                    .uri("${services.position-service.url:http://position-service:8080}")
                )
                
                // Notification Service Routes
                .route("notification-service", r -> r
                    .path("/api/notifications/**")
                    .filters(f -> f
                        .stripPrefix(0)
                        .addRequestHeader("X-Gateway", "api-gateway")
                        .circuitBreaker(config -> config
                            .setName("notification-service-cb")
                            .setFallbackUri("forward:/fallback/notification-service")
                        )
                    )
                    .uri("${services.notification-service.url:http://notification-service:8080}")
                )
                
                // Health check routes (bypass authentication)
                .route("health-checks", r -> r
                    .path("/actuator/**")
                    .filters(f -> f
                        .stripPrefix(0)
                        .addRequestHeader("X-Gateway", "api-gateway")
                    )
                    .uri("http://localhost:8080")
                )
                
                // WebSocket routes for real-time updates
                .route("websocket-risk", r -> r
                    .path("/ws/risk/**")
                    .filters(f -> f
                        .stripPrefix(0)
                        .addRequestHeader("X-Gateway", "api-gateway")
                    )
                    .uri("ws://risk-monitoring-service:8080")
                )
                
                .route("websocket-notifications", r -> r
                    .path("/ws/notifications/**")
                    .filters(f -> f
                        .stripPrefix(0)
                        .addRequestHeader("X-Gateway", "api-gateway")
                    )
                    .uri("ws://notification-service:8080")
                )
                
                .build();
    }
}
