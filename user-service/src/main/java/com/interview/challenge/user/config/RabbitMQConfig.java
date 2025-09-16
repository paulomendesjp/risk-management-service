package com.interview.challenge.user.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * RabbitMQ Configuration for User Service
 */
@Configuration
public class RabbitMQConfig {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConfig.class);

    public static final String USER_REGISTRATION_QUEUE = "user.registrations";
    public static final String USER_UPDATES_QUEUE = "user.updates";
    public static final String USER_DELETIONS_QUEUE = "user.deletions";

    @Bean
    public Queue userRegistrationQueue() {
        logger.info("🐰 Creating user registration queue: {}", USER_REGISTRATION_QUEUE);
        return new Queue(USER_REGISTRATION_QUEUE, true);
    }

    @Bean
    public Queue userUpdatesQueue() {
        logger.info("🐰 Creating user updates queue: {}", USER_UPDATES_QUEUE);
        return new Queue(USER_UPDATES_QUEUE, true);
    }

    @Bean
    public Queue userDeletionsQueue() {
        logger.info("🐰 Creating user deletions queue: {}", USER_DELETIONS_QUEUE);
        return new Queue(USER_DELETIONS_QUEUE, true);
    }

    @Bean
    @Primary
    public MessageConverter jsonMessageConverter() {
        logger.info("🔧 Configuring Jackson2JsonMessageConverter for RabbitMQ");
        // Configure ObjectMapper for Java Time support
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        // Create converter with configured ObjectMapper
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        return converter;
    }

    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        logger.info("🔧 Configuring RabbitTemplate with JSON message converter");
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        logger.info("✅ RabbitTemplate configured with JSON converter");
        return template;
    }
}