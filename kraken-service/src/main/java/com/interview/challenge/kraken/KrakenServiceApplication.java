package com.interview.challenge.kraken;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Kraken Service Application
 *
 * Provides integration with Kraken Futures API for:
 * - Real-time balance monitoring
 * - Order placement and management
 * - Position tracking
 * - Risk management integration
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableRabbit
@EnableFeignClients(basePackages = "com.interview.challenge")
@EnableMongoRepositories
@ComponentScan(basePackages = {"com.interview.challenge.kraken", "com.interview.challenge.shared"})
public class KrakenServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KrakenServiceApplication.class, args);
    }
}