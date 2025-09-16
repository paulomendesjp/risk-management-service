package com.interview.challenge.risk.config;

import com.interview.challenge.risk.websocket.PythonBridgeWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.*;

/**
 * 🌐 RAW WEBSOCKET CONFIGURATION
 *
 * Configures raw WebSocket endpoints for Python Bridge:
 * - Python Bridge connects for real-time data streaming
 * - Direct WebSocket communication without STOMP protocol
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PythonBridgeWebSocketHandler pythonBridgeHandler;

    public WebSocketConfig(PythonBridgeWebSocketHandler pythonBridgeHandler) {
        this.pythonBridgeHandler = pythonBridgeHandler;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        // Register WebSocket endpoint specifically for Python Bridge
        registry.addHandler(pythonBridgeHandler, "/python-bridge")
                .setAllowedOriginPatterns("*");

        // Alternative endpoint for Python Bridge
        registry.addHandler(pythonBridgeHandler, "/bridge-ws")
                .setAllowedOriginPatterns("*");
    }
}