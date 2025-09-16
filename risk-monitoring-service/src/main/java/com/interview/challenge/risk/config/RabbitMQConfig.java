package com.interview.challenge.risk.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 🐰 RABBITMQ CONFIGURATION
 *
 * Configures message queues for event-driven communication:
 * - Risk violation events
 * - Position closure notifications
 * - Balance update events
 * - System alerts
 */
@Configuration
public class RabbitMQConfig {

    // Exchange names
    public static final String RISK_EXCHANGE = "risk.exchange";
    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String POSITION_EXCHANGE = "position.exchange";

    // Queue names
    public static final String USER_REGISTRATION_QUEUE = "user.registrations";
    public static final String RISK_VIOLATION_QUEUE = "risk.violation.queue";
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String POSITION_CLOSURE_QUEUE = "position.closure.queue";
    public static final String BALANCE_UPDATE_QUEUE = "balance.update.queue";

    // Routing keys
    public static final String RISK_VIOLATION_KEY = "risk.violation";
    public static final String DAILY_RISK_KEY = "risk.daily";
    public static final String MAX_RISK_KEY = "risk.max";
    public static final String NOTIFICATION_KEY = "notification.send";
    public static final String POSITION_CLOSE_KEY = "position.close";
    public static final String BALANCE_UPDATE_KEY = "balance.update";

    // Exchanges
    @Bean
    public TopicExchange riskExchange() {
        return new TopicExchange(RISK_EXCHANGE);
    }

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE);
    }

    @Bean
    public TopicExchange positionExchange() {
        return new TopicExchange(POSITION_EXCHANGE);
    }

    // Queues
    @Bean
    public Queue userRegistrationQueue() {
        return new Queue(USER_REGISTRATION_QUEUE, true);
    }

    @Bean
    public Queue riskViolationQueue() {
        return QueueBuilder.durable(RISK_VIOLATION_QUEUE)
                .ttl(86400000) // 24 hours TTL
                .maxLength(10000) // Max 10k messages
                .build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .ttl(3600000) // 1 hour TTL
                .maxLength(5000)
                .build();
    }

    @Bean
    public Queue positionClosureQueue() {
        return QueueBuilder.durable(POSITION_CLOSURE_QUEUE)
                .ttl(86400000) // 24 hours TTL
                .maxLength(5000)
                .build();
    }

    @Bean
    public Queue balanceUpdateQueue() {
        return QueueBuilder.durable(BALANCE_UPDATE_QUEUE)
                .ttl(3600000) // 1 hour TTL
                .maxLength(10000)
                .build();
    }

    // Bindings
    @Bean
    public Binding riskViolationBinding() {
        return BindingBuilder
                .bind(riskViolationQueue())
                .to(riskExchange())
                .with("risk.*");
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(notificationExchange())
                .with(NOTIFICATION_KEY);
    }

    @Bean
    public Binding positionClosureBinding() {
        return BindingBuilder
                .bind(positionClosureQueue())
                .to(positionExchange())
                .with(POSITION_CLOSE_KEY);
    }

    @Bean
    public Binding balanceUpdateBinding() {
        return BindingBuilder
                .bind(balanceUpdateQueue())
                .to(riskExchange())
                .with(BALANCE_UPDATE_KEY);
    }

    // Cross-exchange bindings for risk events to trigger notifications
    @Bean
    public Binding riskToNotificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(riskExchange())
                .with("risk.violation");
    }

    // Message converter for JSON
    @Bean
    public MessageConverter jsonMessageConverter() {
        // Configure ObjectMapper for Java Time support
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        // Create converter with configured ObjectMapper
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        return converter;
    }

    // RabbitTemplate configuration
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }

    // Listener container factory for JSON message conversion
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        return factory;
    }
}