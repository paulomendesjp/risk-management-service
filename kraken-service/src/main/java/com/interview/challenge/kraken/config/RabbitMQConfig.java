package com.interview.challenge.kraken.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for Kraken Service
 * Configura filas para processamento assíncrono de webhooks
 */
@Configuration
public class RabbitMQConfig {

    // Filas
    public static final String KRAKEN_ORDERS_QUEUE = "kraken.orders.queue";
    public static final String KRAKEN_ORDERS_DLQ = "kraken.orders.dlq";

    // Exchange
    public static final String TRADING_EXCHANGE = "trading.exchange";

    // Routing Keys
    public static final String KRAKEN_ROUTING_KEY = "trading.kraken";

    /**
     * Fila principal para ordens do Kraken
     */
    @Bean
    public Queue krakenOrdersQueue() {
        return QueueBuilder.durable(KRAKEN_ORDERS_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", KRAKEN_ORDERS_DLQ)
                .withArgument("x-message-ttl", 300000) // 5 minutos TTL
                .build();
    }

    /**
     * Dead Letter Queue para mensagens com erro
     */
    @Bean
    public Queue krakenOrdersDLQ() {
        return QueueBuilder.durable(KRAKEN_ORDERS_DLQ)
                .withArgument("x-message-ttl", 86400000) // 24 horas TTL
                .build();
    }

    /**
     * Exchange para trading
     */
    @Bean
    public TopicExchange tradingExchange() {
        return new TopicExchange(TRADING_EXCHANGE, true, false);
    }

    /**
     * Binding da fila com exchange
     */
    @Bean
    public Binding krakenOrdersBinding(Queue krakenOrdersQueue, TopicExchange tradingExchange) {
        return BindingBuilder
                .bind(krakenOrdersQueue)
                .to(tradingExchange)
                .with(KRAKEN_ROUTING_KEY);
    }

    /**
     * Conversor JSON para mensagens
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Template do RabbitMQ com conversor JSON
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    /**
     * Configuração do listener container factory com conversor JSON
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }

    // User registration configuration
    @Bean
    public FanoutExchange userRegistrationsExchange() {
        return new FanoutExchange("user.registrations");
    }

    @Bean
    public FanoutExchange userUpdatesExchange() {
        return new FanoutExchange("user.updates");
    }

    @Bean
    public FanoutExchange userDeletionsExchange() {
        return new FanoutExchange("user.deletions");
    }

    @Bean
    public Queue userRegistrationsQueue() {
        return new Queue("kraken.user.registrations", true);
    }

    @Bean
    public Queue userUpdatesQueue() {
        return new Queue("kraken.user.updates", true);
    }

    @Bean
    public Queue userDeletionsQueue() {
        return new Queue("kraken.user.deletions", true);
    }

    @Bean
    public Binding userRegistrationsBinding(Queue userRegistrationsQueue, FanoutExchange userRegistrationsExchange) {
        return BindingBuilder.bind(userRegistrationsQueue).to(userRegistrationsExchange);
    }

    @Bean
    public Binding userUpdatesBinding(Queue userUpdatesQueue, FanoutExchange userUpdatesExchange) {
        return BindingBuilder.bind(userUpdatesQueue).to(userUpdatesExchange);
    }

    @Bean
    public Binding userDeletionsBinding(Queue userDeletionsQueue, FanoutExchange userDeletionsExchange) {
        return BindingBuilder.bind(userDeletionsQueue).to(userDeletionsExchange);
    }
}