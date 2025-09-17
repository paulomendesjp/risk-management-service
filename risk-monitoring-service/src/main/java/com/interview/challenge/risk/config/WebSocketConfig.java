package com.interview.challenge.risk.config;

// import com.interview.challenge.risk.websocket.PythonBridgeWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.*;

/**
 * 🌐 RAW WEBSOCKET CONFIGURATION
 *
 * DISABLED: Python Bridge WebSocket connection removed in favor of polling
 * - Now using scheduled polling to fetch balance updates
 * - See RiskMonitoringService.performPeriodicBalancePoll()
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    // DISABLED: Python Bridge WebSocket handler removed
    // private final PythonBridgeWebSocketHandler pythonBridgeHandler;

    public WebSocketConfig(/* PythonBridgeWebSocketHandler pythonBridgeHandler */) {
        // this.pythonBridgeHandler = pythonBridgeHandler;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        // DISABLED: Python Bridge WebSocket endpoints removed in favor of polling
        // registry.addHandler(pythonBridgeHandler, "/python-bridge")
        //         .setAllowedOriginPatterns("*");

        // registry.addHandler(pythonBridgeHandler, "/bridge-ws")
        //         .setAllowedOriginPatterns("*");
    }
}