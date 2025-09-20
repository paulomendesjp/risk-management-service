package com.interview.challenge.kraken.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.challenge.kraken.service.KrakenCredentialManager;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket V2 Client for Kraken Private Channels
 *
 * Implements real-time balance monitoring via WebSocket instead of polling
 * Uses authenticated WebSocket connections for private data
 */
@Slf4j
@Component
public class KrakenWebSocketClient extends WebSocketClient {

    private static final String KRAKEN_WS_URL = "wss://ws-auth.kraken.com/v2";

    @Autowired
    private KrakenWebSocketHandler messageHandler;

    @Autowired
    private KrakenCredentialManager credentialManager;

    @Autowired
    private com.interview.challenge.kraken.client.KrakenWebSocketTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${kraken.websocket.reconnect.interval:5000}")
    private int reconnectInterval;

    // Store active subscriptions per client
    private final Map<String, ClientSubscription> activeSubscriptions = new ConcurrentHashMap<>();

    // Store WebSocket connection state
    private volatile boolean isConnected = false;
    private volatile boolean isAuthenticated = false;

    public KrakenWebSocketClient() throws Exception {
        super(new URI(KRAKEN_WS_URL));
        this.setConnectionLostTimeout(0);
        this.setTcpNoDelay(true);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("🔌 WebSocket connection opened to Kraken");
        isConnected = true;

        // Authenticate all pending clients
        authenticatePendingClients();
    }

    @Override
    public void onMessage(String message) {
        try {
            log.debug("📨 WebSocket message received: {}", message);

            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            String channel = (String) data.get("channel");
            String type = (String) data.get("type");

            if ("status".equals(type)) {
                handleStatusMessage(data);
            } else if ("balances".equals(channel)) {
                handleBalanceUpdate(data);
            } else if ("openOrders".equals(channel)) {
                handleOrderUpdate(data);
            } else if ("ownTrades".equals(channel)) {
                handleTradeUpdate(data);
            } else if ("error".equals(type)) {
                handleError(data);
            }

        } catch (Exception e) {
            log.error("❌ Error processing WebSocket message: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("🔌 WebSocket connection closed: {} - {}", code, reason);
        isConnected = false;
        isAuthenticated = false;

        // Schedule reconnection
        scheduleReconnect();
    }

    @Override
    public void onError(Exception ex) {
        log.error("❌ WebSocket error: {}", ex.getMessage(), ex);
    }

    /**
     * Subscribe to balance updates for a specific client
     */
    public void subscribeToBalances(String clientId, String apiKey, String apiSecret) {
        try {
            log.info("📡 Subscribing client {} to Kraken Futures WebSocket", clientId);

            // Kraken Futures doesn't use token-based auth for WebSocket
            // Authentication is done via API key/secret in the subscribe message

            // Store subscription info (no token needed for Futures)
            activeSubscriptions.put(clientId, new ClientSubscription(clientId, apiKey, apiSecret, null));

            if (isConnected) {
                // Send subscription request with API credentials
                sendAuthenticatedSubscription(clientId, apiKey, apiSecret);
            } else {
                // Connect if not connected
                log.info("🔌 Connecting to WebSocket...");
                this.connect();
            }

        } catch (Exception e) {
            log.error("❌ Failed to subscribe client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Unsubscribe from balance updates
     */
    public void unsubscribeFromBalances(String clientId) {
        try {
            activeSubscriptions.remove(clientId);

            if (isConnected) {
                Map<String, Object> unsubscribe = new HashMap<>();
                unsubscribe.put("method", "unsubscribe");
                unsubscribe.put("params", Map.of(
                    "channel", "balances",
                    "snapshot", false
                ));
                unsubscribe.put("req_id", generateReqId());

                String message = objectMapper.writeValueAsString(unsubscribe);
                this.send(message);

                log.info("📤 Unsubscribed client {} from balance updates", clientId);
            }

        } catch (Exception e) {
            log.error("❌ Failed to unsubscribe client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * NOT USED - Kraken uses token-based auth, not direct auth
     * @deprecated Use token from REST API instead
     */
    @Deprecated
    private void authenticate(String apiKey, String apiSecret) {
        // Kraken WebSocket V2 doesn't support direct authentication
        // Must use token from REST API /0/private/GetWebSocketsToken
        log.warn("Direct authentication not supported - use token from REST API");
    }

    /**
     * Generate authentication message for Kraken WebSocket
     */
    private String generateAuthMessage(String apiKey, String apiSecret, String nonce) throws Exception {
        // Kraken WebSocket authentication format
        String payload = "WSS" + nonce;

        // Create SHA-256 hash
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(payload.getBytes());

        // Create HMAC-SHA512
        Mac mac = Mac.getInstance("HmacSHA512");
        SecretKeySpec secretKey = new SecretKeySpec(
            Base64.getDecoder().decode(apiSecret),
            "HmacSHA512"
        );
        mac.init(secretKey);

        byte[] signature = mac.doFinal(hash);

        // Create JWT-like token
        Map<String, String> token = new HashMap<>();
        token.put("api_key", apiKey);
        token.put("signature", Base64.getEncoder().encodeToString(signature));
        token.put("nonce", nonce);

        return objectMapper.writeValueAsString(token);
    }

    /**
     * Send authenticated subscription for Kraken Futures
     */
    private void sendAuthenticatedSubscription(String clientId, String apiKey, String apiSecret) {
        try {
            // For Kraken Futures, we don't need a token - use API key/secret directly
            // The Futures API uses different authentication mechanism

            Map<String, Object> subscribe = new HashMap<>();
            subscribe.put("method", "subscribe");
            subscribe.put("feed", "balances");
            subscribe.put("api_key", apiKey);

            // Generate nonce and signature
            String nonce = String.valueOf(System.currentTimeMillis());
            String message = nonce + "subscribe" + "balances";
            String signature = generateSignature(apiSecret, message);

            subscribe.put("nonce", nonce);
            subscribe.put("signature", signature);
            subscribe.put("req_id", generateReqId());

            String jsonMessage = objectMapper.writeValueAsString(subscribe);
            log.info("📤 Sending authenticated subscription for client: {}", clientId);
            this.send(jsonMessage);

        } catch (Exception e) {
            log.error("❌ Failed to send authenticated subscription for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Generate HMAC signature for Kraken Futures
     */
    private String generateSignature(String apiSecret, String message) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA512");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(
                apiSecret.getBytes(), "HmacSHA512");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(message.getBytes());
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to generate signature: {}", e.getMessage());
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    /**
     * Send subscription request for a client using token (for Spot API, not Futures)
     */
    private void sendSubscriptionRequest(String clientId, String token) {
        try {
            // Subscribe to multiple private channels with token
            List<String> channels = Arrays.asList("balances", "openOrders", "ownTrades");

            for (String channel : channels) {
                Map<String, Object> subscribe = new HashMap<>();
                subscribe.put("method", "subscribe");
                subscribe.put("params", Map.of(
                    "channel", channel,
                    "token", token,  // Use token for authentication
                    "snapshot", true
                ));
                subscribe.put("req_id", generateReqId());

                String message = objectMapper.writeValueAsString(subscribe);
                this.send(message);

                log.info("📤 Subscribed to {} channel for client: {} (with token)", channel, clientId);
            }

            isAuthenticated = true;

        } catch (Exception e) {
            log.error("❌ Failed to send subscription request: {}", e.getMessage());
        }
    }

    /**
     * Handle balance update from WebSocket
     */
    private void handleBalanceUpdate(Map<String, Object> data) {
        try {
            List<Map<String, Object>> balances = (List<Map<String, Object>>) data.get("data");

            for (Map<String, Object> balance : balances) {
                String asset = (String) balance.get("asset");
                String amount = (String) balance.get("balance");

                // Find which client this belongs to (may need additional logic)
                for (ClientSubscription sub : activeSubscriptions.values()) {
                    // Notify handler about balance update
                    messageHandler.handleBalanceUpdate(
                        sub.getClientId(),
                        asset,
                        amount,
                        data
                    );
                }
            }

        } catch (Exception e) {
            log.error("❌ Error handling balance update: {}", e.getMessage());
        }
    }

    /**
     * Handle order update from WebSocket
     */
    private void handleOrderUpdate(Map<String, Object> data) {
        try {
            // Process order updates
            List<Map<String, Object>> orders = (List<Map<String, Object>>) data.get("data");

            for (Map<String, Object> order : orders) {
                String orderId = (String) order.get("order_id");
                String status = (String) order.get("status");

                log.info("📊 Order update: {} - {}", orderId, status);

                // Notify handler
                for (ClientSubscription sub : activeSubscriptions.values()) {
                    messageHandler.handleOrderUpdate(sub.getClientId(), order);
                }
            }

        } catch (Exception e) {
            log.error("❌ Error handling order update: {}", e.getMessage());
        }
    }

    /**
     * Handle trade update from WebSocket
     */
    private void handleTradeUpdate(Map<String, Object> data) {
        try {
            List<Map<String, Object>> trades = (List<Map<String, Object>>) data.get("data");

            for (Map<String, Object> trade : trades) {
                String tradeId = (String) trade.get("trade_id");
                String pair = (String) trade.get("pair");

                log.info("💹 Trade executed: {} - {}", tradeId, pair);

                // Notify handler
                for (ClientSubscription sub : activeSubscriptions.values()) {
                    messageHandler.handleTradeUpdate(sub.getClientId(), trade);
                }
            }

        } catch (Exception e) {
            log.error("❌ Error handling trade update: {}", e.getMessage());
        }
    }

    /**
     * Handle status messages
     */
    private void handleStatusMessage(Map<String, Object> data) {
        String status = (String) data.get("status");

        if ("online".equals(status)) {
            isAuthenticated = true;
            log.info("✅ WebSocket authenticated and online");

            // Subscribe all pending clients
            authenticatePendingClients();
        }
    }

    /**
     * Handle error messages
     */
    private void handleError(Map<String, Object> data) {
        String error = (String) data.get("error");
        log.error("❌ WebSocket error received: {}", error);
    }

    /**
     * Authenticate all pending clients
     */
    private void authenticatePendingClients() {
        for (ClientSubscription sub : activeSubscriptions.values()) {
            // For Kraken Futures, we don't use tokens
            // Send authenticated subscription directly
            sendAuthenticatedSubscription(sub.getClientId(), sub.getApiKey(), sub.getApiSecret());
        }
    }

    /**
     * Schedule reconnection attempt
     */
    private void scheduleReconnect() {
        new Thread(() -> {
            try {
                Thread.sleep(reconnectInterval);
                log.info("🔄 Attempting to reconnect WebSocket...");
                this.reconnect();
            } catch (Exception e) {
                log.error("❌ Reconnection failed: {}", e.getMessage());
            }
        }).start();
    }

    /**
     * Generate unique request ID
     */
    private String generateReqId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Inner class to store client subscription info
     */
    private static class ClientSubscription {
        private final String clientId;
        private final String apiKey;
        private final String apiSecret;
        private final String token;
        private final long tokenExpiry;

        public ClientSubscription(String clientId, String apiKey, String apiSecret, String token) {
            this.clientId = clientId;
            this.apiKey = apiKey;
            this.apiSecret = apiSecret;
            this.token = token;
            // Token valid for 15 minutes
            this.tokenExpiry = System.currentTimeMillis() + (15 * 60 * 1000);
        }

        public String getClientId() { return clientId; }
        public String getApiKey() { return apiKey; }
        public String getApiSecret() { return apiSecret; }
        public String getToken() { return token; }

        public boolean isTokenExpired() {
            return System.currentTimeMillis() > tokenExpiry;
        }
    }
}