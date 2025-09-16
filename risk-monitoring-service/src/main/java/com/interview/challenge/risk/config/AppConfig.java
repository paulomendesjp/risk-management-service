package com.interview.challenge.risk.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Application configuration for Risk Monitoring Service
 */
@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // RabbitMQ Queues
    @Bean
    public Queue userRegistrationsQueue() {
        return new Queue("user.registrations", true);
    }

    @Bean
    public Queue userUpdatesQueue() {
        return new Queue("user.updates", true);
    }

    @Bean
    public Queue userDeletionsQueue() {
        return new Queue("user.deletions", true);
    }
}