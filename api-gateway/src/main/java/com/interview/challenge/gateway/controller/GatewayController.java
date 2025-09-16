package com.interview.challenge.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/gateway")
@RequiredArgsConstructor
@Slf4j
public class GatewayController {

    private final RouteLocator routeLocator;

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("service", "API Gateway");
        response.put("message", "Gateway is running and healthy");

        log.debug("Health check requested - Gateway is UP");
        return Mono.just(ResponseEntity.ok(response));
    }

    @GetMapping("/routes")
    public Flux<Map<String, Object>> getRoutes() {
        log.debug("Fetching all configured routes");

        return routeLocator.getRoutes()
                .map(route -> {
                    Map<String, Object> routeInfo = new HashMap<>();
                    routeInfo.put("id", route.getId());
                    routeInfo.put("uri", route.getUri().toString());
                    routeInfo.put("order", route.getOrder());
                    routeInfo.put("predicates", route.getPredicate().toString());

                    // Get filter information
                    if (route.getFilters() != null && !route.getFilters().isEmpty()) {
                        routeInfo.put("filters", route.getFilters().stream()
                                .map(filter -> filter.toString())
                                .toList());
                    }

                    return routeInfo;
                });
    }

    @GetMapping("/info")
    public Mono<Map<String, Object>> getGatewayInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "Trading Risk Management API Gateway");
        info.put("version", "1.0.0");
        info.put("description", "Central entry point for all microservices");
        info.put("timestamp", LocalDateTime.now().toString());

        Map<String, String> services = new HashMap<>();
        services.put("user-service", "/api/users/**");
        services.put("risk-monitoring", "/api/risk/**");
        services.put("position-service", "/api/positions/**");
        services.put("notification-service", "/api/notifications/**");
        info.put("services", services);

        Map<String, String> features = new HashMap<>();
        features.put("authentication", "JWT Token Based");
        features.put("rate-limiting", "Redis Based Rate Limiting");
        features.put("circuit-breaker", "Resilience4J");
        features.put("monitoring", "Prometheus & Actuator");
        info.put("features", features);

        log.debug("Gateway info requested");
        return Mono.just(info);
    }

    @GetMapping("/status")
    public Mono<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("gateway", "OPERATIONAL");
        status.put("timestamp", LocalDateTime.now().toString());

        // In a real scenario, you might check the health of dependent services
        Map<String, String> serviceStatus = new HashMap<>();
        serviceStatus.put("user-service", "UNKNOWN");
        serviceStatus.put("risk-monitoring", "UNKNOWN");
        serviceStatus.put("position-service", "UNKNOWN");
        serviceStatus.put("notification-service", "UNKNOWN");
        status.put("services", serviceStatus);

        return Mono.just(status);
    }
}