package com.interview.challenge.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 🐰 RABBITMQ CONFIGURATION FOR NOTIFICATION SERVICE
 * 
 * Configures queues and exchanges for centralized notification handling
 */
@Configuration
@EnableRabbit
public class RabbitConfig {

    // Queue names
    public static final String NOTIFICATIONS_QUEUE = "notifications";
    public static final String NOTIFICATIONS_DLQ = "notifications.dlq";
    
    // Exchange names
    public static final String NOTIFICATIONS_EXCHANGE = "notifications.exchange";
    public static final String DLX_EXCHANGE = "dlx.exchange";


    /**
     * Main notifications queue
     * Receives all notification events from risk monitoring service
     */
    @Bean
    public Queue notificationsQueue() {
        return QueueBuilder
                .durable(NOTIFICATIONS_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "notifications.failed")
                .withArgument("x-message-ttl", 300000) // 5 minutes
                .build();
    }

    /**
     * Dead letter queue for failed notifications
     */
    @Bean
    public Queue notificationsDeadLetterQueue() {
        return QueueBuilder
                .durable(NOTIFICATIONS_DLQ)
                .build();
    }

    /**
     * Notifications exchange
     */
    @Bean
    public TopicExchange notificationsExchange() {
        return ExchangeBuilder
                .topicExchange(NOTIFICATIONS_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * Dead letter exchange
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder
                .directExchange(DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * Bind notifications queue to exchange
     */
    @Bean
    public Binding notificationsBinding() {
        return BindingBuilder
                .bind(notificationsQueue())
                .to(notificationsExchange())
                .with("notification.*");
    }

    /**
     * Bind dead letter queue to DLX
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(notificationsDeadLetterQueue())
                .to(deadLetterExchange())
                .with("notifications.failed");
    }
}
