package com.interview.challenge.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 🐰 NOTIFICATION SERVICE RABBITMQ CONFIGURATION
 *
 * Configures message queues for receiving and processing notifications:
 * - Listens to risk violation events
 * - Processes notification requests
 * - Handles multi-channel delivery
 */
@Configuration
public class RabbitMQConfig {

    // Exchange names (must match risk-monitoring-service)
    public static final String RISK_EXCHANGE = "risk.exchange";
    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";

    // Queue names
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String EMAIL_QUEUE = "notification.email.queue";
    public static final String SLACK_QUEUE = "notification.slack.queue";
    public static final String WEBSOCKET_QUEUE = "notification.websocket.queue";

    // Routing keys
    public static final String NOTIFICATION_KEY = "notification.send";
    public static final String EMAIL_KEY = "notification.email";
    public static final String SLACK_KEY = "notification.slack";
    public static final String WEBSOCKET_KEY = "notification.websocket";

    // Exchanges
    @Bean
    public TopicExchange riskExchange() {
        return new TopicExchange(RISK_EXCHANGE);
    }

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE);
    }

    // Main notification queue
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .ttl(3600000) // 1 hour TTL
                .maxLength(5000)
                .build();
    }

    // Channel-specific queues
    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE)
                .ttl(86400000) // 24 hours TTL
                .maxLength(1000)
                .build();
    }

    @Bean
    public Queue slackQueue() {
        return QueueBuilder.durable(SLACK_QUEUE)
                .ttl(3600000) // 1 hour TTL
                .maxLength(1000)
                .build();
    }

    @Bean
    public Queue websocketQueue() {
        return QueueBuilder.durable(WEBSOCKET_QUEUE)
                .ttl(600000) // 10 minutes TTL
                .maxLength(5000)
                .build();
    }

    // Bindings for main notification queue
    @Bean
    public Binding notificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(notificationExchange())
                .with(NOTIFICATION_KEY);
    }

    // Listen to risk violation events from risk-monitoring-service
    @Bean
    public Binding riskViolationNotificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(riskExchange())
                .with("risk.violation");
    }

    @Bean
    public Binding dailyRiskNotificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(riskExchange())
                .with("risk.daily");
    }

    @Bean
    public Binding maxRiskNotificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(riskExchange())
                .with("risk.max");
    }

    // Channel-specific bindings
    @Bean
    public Binding emailBinding() {
        return BindingBuilder
                .bind(emailQueue())
                .to(notificationExchange())
                .with(EMAIL_KEY);
    }

    @Bean
    public Binding slackBinding() {
        return BindingBuilder
                .bind(slackQueue())
                .to(notificationExchange())
                .with(SLACK_KEY);
    }

    @Bean
    public Binding websocketBinding() {
        return BindingBuilder
                .bind(websocketQueue())
                .to(notificationExchange())
                .with(WEBSOCKET_KEY);
    }

    // Message converter for JSON with LocalDateTime support
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    // RabbitTemplate configuration
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}