package com.interview.challenge.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Risk Monitoring Service Application
 *
 * Main entry point for the Risk Monitoring microservice
 * Enables real-time risk analysis and automated position management
 */
@SpringBootApplication(scanBasePackages = {"com.interview.challenge.risk", "com.interview.challenge.shared"})
@EnableFeignClients(basePackages = {"com.interview.challenge.shared"})
@EnableAsync
@EnableScheduling
public class RiskMonitoringServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskMonitoringServiceApplication.class, args);
    }
}