package com.interview.challenge.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
}
