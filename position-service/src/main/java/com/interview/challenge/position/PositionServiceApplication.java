package com.interview.challenge.position;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Position Management Service
 * 
 * Responsibilities:
 * - Integration with Architect.co trading API
 * - Order placement and management
 * - Position closing and liquidation
 * - Real-time balance monitoring
 * - Risk event processing via RabbitMQ
 */
@SpringBootApplication(scanBasePackages = {
    "com.interview.challenge.position",
    "com.interview.challenge.shared"
})
@EnableFeignClients(basePackages = {
    "com.interview.challenge.position", 
    "com.interview.challenge.shared.client"
})
@EnableMongoRepositories
@EnableAsync
@EnableScheduling
public class PositionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PositionServiceApplication.class, args);
    }
}




