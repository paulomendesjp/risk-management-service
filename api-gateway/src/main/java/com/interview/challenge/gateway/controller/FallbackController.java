package com.interview.challenge.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
@Slf4j
public class FallbackController {

    @GetMapping("/user-service")
    public Mono<Map<String, Object>> userServiceFallback() {
        log.warn("User service is unavailable - returning fallback response");
        return createFallbackResponse("User Service", "The user service is temporarily unavailable");
    }

    @PostMapping("/user-service")
    public Mono<Map<String, Object>> userServicePostFallback() {
        log.warn("User service is unavailable - returning fallback response for POST");
        return createFallbackResponse("User Service", "The user service is temporarily unavailable");
    }

    @GetMapping("/risk-monitoring")
    public Mono<Map<String, Object>> riskMonitoringFallback() {
        log.warn("Risk monitoring service is unavailable - returning fallback response");
        return createFallbackResponse("Risk Monitoring Service", "The risk monitoring service is temporarily unavailable");
    }

    @PostMapping("/risk-monitoring")
    public Mono<Map<String, Object>> riskMonitoringPostFallback() {
        log.warn("Risk monitoring service is unavailable - returning fallback response for POST");
        return createFallbackResponse("Risk Monitoring Service", "The risk monitoring service is temporarily unavailable");
    }

    @GetMapping("/position-service")
    public Mono<Map<String, Object>> positionServiceFallback() {
        log.warn("Position service is unavailable - returning fallback response");
        return createFallbackResponse("Position Service", "The position service is temporarily unavailable");
    }

    @PostMapping("/position-service")
    public Mono<Map<String, Object>> positionServicePostFallback() {
        log.warn("Position service is unavailable - returning fallback response for POST");
        return createFallbackResponse("Position Service", "The position service is temporarily unavailable");
    }

    @GetMapping("/notification-service")
    public Mono<Map<String, Object>> notificationServiceFallback() {
        log.warn("Notification service is unavailable - returning fallback response");
        return createFallbackResponse("Notification Service", "The notification service is temporarily unavailable");
    }

    @PostMapping("/notification-service")
    public Mono<Map<String, Object>> notificationServicePostFallback() {
        log.warn("Notification service is unavailable - returning fallback response for POST");
        return createFallbackResponse("Notification Service", "The notification service is temporarily unavailable");
    }

    @RequestMapping("/**")
    public Mono<Map<String, Object>> genericFallback() {
        log.warn("Generic fallback triggered for unknown service");
        return createFallbackResponse("Unknown Service", "The requested service is temporarily unavailable");
    }

    private Mono<Map<String, Object>> createFallbackResponse(String service, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", "Service Unavailable");
        response.put("message", message);
        response.put("service", service);
        response.put("fallback", true);
        response.put("info", "Please try again later. If the problem persists, contact support.");

        return Mono.just(response);
    }
}