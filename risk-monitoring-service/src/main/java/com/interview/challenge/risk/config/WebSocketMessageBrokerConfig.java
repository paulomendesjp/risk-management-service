package com.interview.challenge.risk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * 🌐 STOMP WEBSOCKET CONFIGURATION
 *
 * Configures STOMP WebSocket endpoints for frontend clients:
 * - Frontend clients can subscribe to balance updates
 * - Risk alerts are broadcast in real-time
 * - System status updates are pushed to connected clients
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketMessageBrokerConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        // Enable simple broker for topics
        config.enableSimpleBroker("/topic", "/queue");

        // Set application destination prefix
        config.setApplicationDestinationPrefixes("/app");

        // Set user destination prefix for private messages
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        // Register WebSocket endpoint for frontend clients (STOMP)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Register endpoint without SockJS for native WebSocket clients
        registry.addEndpoint("/websocket")
                .setAllowedOriginPatterns("*");
    }
}