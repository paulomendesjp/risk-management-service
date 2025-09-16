package com.interview.challenge.position.config;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * RabbitMQ configuration for Position Service
 * Configures exchanges, queues, and message handling
 */
@Configuration
@EnableRabbit
public class RabbitConfig {

    // Exchange names
    public static final String RISK_MANAGEMENT_EXCHANGE = "risk.management.exchange";
    public static final String POSITION_MANAGEMENT_EXCHANGE = "position.management.exchange";
    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String DLX_EXCHANGE = "dlx.exchange";

    // Queue names
    public static final String POSITION_CLOSE_DAILY_QUEUE = "position.close.daily.queue";
    public static final String POSITION_CLOSE_MAX_QUEUE = "position.close.max.queue";
    public static final String POSITION_CLOSE_MANUAL_QUEUE = "position.close.manual.queue";
    
    // Dead letter queues
    public static final String POSITION_CLOSE_FAILED_QUEUE = "position.close.failed.queue";
    
    // Routing keys
    public static final String RISK_VIOLATION_DAILY_KEY = "risk.violation.daily";
    public static final String RISK_VIOLATION_MAX_KEY = "risk.violation.max";
    public static final String POSITION_CLOSE_MANUAL_KEY = "position.close.manual";
    public static final String POSITION_CLOSED_KEY = "position.closed";
    public static final String POSITION_FAILED_KEY = "position.failed";

    /**
     * JSON message converter for RabbitMQ
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate with JSON converter
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    /**
     * Retry template for failed message processing
     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Retry policy
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        // Backoff policy
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(10000);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        return retryTemplate;
    }

    /**
     * Rabbit listener container factory with retry configuration
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            RetryTemplate retryTemplate) {
        
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        factory.setRetryTemplate(retryTemplate);
        // Note: Recovery callback is now handled by the retry template
        
        return factory;
    }

    // ========== EXCHANGES ==========

    /**
     * Risk Management Exchange - receives risk violation events
     */
    @Bean
    public TopicExchange riskManagementExchange() {
        return ExchangeBuilder
                .topicExchange(RISK_MANAGEMENT_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * Position Management Exchange - publishes position events
     */
    @Bean
    public TopicExchange positionManagementExchange() {
        return ExchangeBuilder
                .topicExchange(POSITION_MANAGEMENT_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * Notification Exchange - publishes notification events
     */
    @Bean
    public TopicExchange notificationExchange() {
        return ExchangeBuilder
                .topicExchange(NOTIFICATION_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * Dead Letter Exchange - for failed messages
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder
                .directExchange(DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    // ========== QUEUES ==========

    /**
     * Queue for daily risk violation events
     */
    @Bean
    public Queue positionCloseDailyQueue() {
        return QueueBuilder
                .durable(POSITION_CLOSE_DAILY_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "position.close.daily.failed")
                .withArgument("x-message-ttl", 300000) // 5 minutes
                .build();
    }

    /**
     * Queue for maximum risk violation events
     */
    @Bean
    public Queue positionCloseMaxQueue() {
        return QueueBuilder
                .durable(POSITION_CLOSE_MAX_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "position.close.max.failed")
                .withArgument("x-message-ttl", 300000) // 5 minutes
                .build();
    }

    /**
     * Queue for manual position close requests
     */
    @Bean
    public Queue positionCloseManualQueue() {
        return QueueBuilder
                .durable(POSITION_CLOSE_MANUAL_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "position.close.manual.failed")
                .withArgument("x-message-ttl", 300000) // 5 minutes
                .build();
    }

    /**
     * Dead letter queue for failed position close messages
     */
    @Bean
    public Queue positionCloseFailedQueue() {
        return QueueBuilder
                .durable(POSITION_CLOSE_FAILED_QUEUE)
                .build();
    }

    // ========== BINDINGS ==========

    /**
     * Bind daily risk violations to position close queue
     */
    @Bean
    public Binding bindingDailyRiskViolation() {
        return BindingBuilder
                .bind(positionCloseDailyQueue())
                .to(riskManagementExchange())
                .with(RISK_VIOLATION_DAILY_KEY);
    }

    /**
     * Bind maximum risk violations to position close queue
     */
    @Bean
    public Binding bindingMaxRiskViolation() {
        return BindingBuilder
                .bind(positionCloseMaxQueue())
                .to(riskManagementExchange())
                .with(RISK_VIOLATION_MAX_KEY);
    }

    /**
     * Bind manual position close requests
     */
    @Bean
    public Binding bindingManualPositionClose() {
        return BindingBuilder
                .bind(positionCloseManualQueue())
                .to(positionManagementExchange())
                .with(POSITION_CLOSE_MANUAL_KEY);
    }

    /**
     * Bind failed messages to dead letter queue
     */
    @Bean
    public Binding bindingFailedMessages() {
        return BindingBuilder
                .bind(positionCloseFailedQueue())
                .to(deadLetterExchange())
                .with("position.close.*.failed");
    }
}












