package com.interview.challenge.risk.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🌐 RISK WEBSOCKET HANDLER
 * 
 * Handles WebSocket communication for real-time risk monitoring:
 * - Broadcasts balance updates to subscribed clients
 * - Sends risk alerts in real-time
 * - Manages client subscriptions
 */
@Component
public class RiskWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(RiskWebSocketHandler.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // Track active subscriptions
    private final Map<String, Integer> clientSubscriptions = new ConcurrentHashMap<>();

    /**
     * 💰 BROADCAST BALANCE UPDATE
     * 
     * Sends real-time balance updates to all subscribed clients
     */
    public void broadcastBalanceUpdate(String clientId, Map<String, Object> balanceData) {
        try {
            String topic = "/topic/balance/" + clientId;
            
            logger.debug("📡 Broadcasting balance update to topic: {}", topic);
            logger.debug("💰 Balance data: {}", balanceData);
            
            messagingTemplate.convertAndSend(topic, balanceData);
            
            // Also broadcast to general balance topic
            messagingTemplate.convertAndSend("/topic/balance", Map.of(
                "clientId", clientId,
                "data", balanceData
            ));
            
        } catch (Exception e) {
            logger.error("❌ Error broadcasting balance update for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * 🚨 BROADCAST RISK ALERT (4-parameter version)
     * 
     * Sends immediate risk alerts to subscribed clients
     */
    public void broadcastRiskAlert(String clientId, String type, String message, Map<String, Object> details) {
        Map<String, Object> riskData = Map.of(
            "type", type,
            "message", message,
            "details", details,
            "timestamp", System.currentTimeMillis()
        );
        broadcastRiskAlert(clientId, riskData);
    }

    /**
     * 🚨 BROADCAST RISK ALERT (2-parameter version)
     * 
     * Sends immediate risk alerts to subscribed clients
     */
    public void broadcastRiskAlert(String clientId, Map<String, Object> riskData) {
        try {
            String topic = "/topic/risk/" + clientId;
            
            logger.info("🚨 Broadcasting risk alert to topic: {}", topic);
            logger.info("⚠️ Risk data: {}", riskData);
            
            messagingTemplate.convertAndSend(topic, riskData);
            
            // Also broadcast to general risk alerts topic
            messagingTemplate.convertAndSend("/topic/alerts", Map.of(
                "clientId", clientId,
                "alert", riskData
            ));
            
        } catch (Exception e) {
            logger.error("❌ Error broadcasting risk alert for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * 📊 BROADCAST ACCOUNT STATUS
     * 
     * Sends comprehensive account status updates
     */
    public void broadcastAccountStatus(String clientId, Map<String, Object> statusData) {
        try {
            String topic = "/topic/status/" + clientId;
            
            logger.debug("📊 Broadcasting account status to topic: {}", topic);
            
            messagingTemplate.convertAndSend(topic, statusData);
            
            // Also broadcast to general status topic
            messagingTemplate.convertAndSend("/topic/status", Map.of(
                "clientId", clientId,
                "status", statusData
            ));
            
        } catch (Exception e) {
            logger.error("❌ Error broadcasting account status for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * 🔔 BROADCAST SYSTEM NOTIFICATION
     * 
     * Sends system-wide notifications
     */
    public void broadcastSystemNotification(String message, String type) {
        try {
            Map<String, Object> notification = Map.of(
                "message", message,
                "type", type,
                "timestamp", System.currentTimeMillis()
            );
            
            logger.info("🔔 Broadcasting system notification: {}", message);
            
            messagingTemplate.convertAndSend("/topic/notifications", notification);
            
        } catch (Exception e) {
            logger.error("❌ Error broadcasting system notification: {}", e.getMessage());
        }
    }

    /**
     * 📈 BROADCAST MONITORING STATUS
     * 
     * Sends monitoring service status updates
     */
    public void broadcastMonitoringStatus(Map<String, Object> statusData) {
        try {
            logger.debug("📈 Broadcasting monitoring status");
            
            messagingTemplate.convertAndSend("/topic/monitoring", statusData);
            
        } catch (Exception e) {
            logger.error("❌ Error broadcasting monitoring status: {}", e.getMessage());
        }
    }

    /**
     * 👤 SEND PRIVATE MESSAGE TO USER
     * 
     * Sends private message to specific user session
     */
    public void sendPrivateMessage(String userId, String message, Map<String, Object> data) {
        try {
            Map<String, Object> privateMessage = Map.of(
                "message", message,
                "data", data,
                "timestamp", System.currentTimeMillis()
            );
            
            logger.debug("👤 Sending private message to user: {}", userId);
            
            messagingTemplate.convertAndSendToUser(userId, "/queue/messages", privateMessage);
            
        } catch (Exception e) {
            logger.error("❌ Error sending private message to user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * 📊 GET CONNECTION STATISTICS
     */
    public Map<String, Object> getConnectionStats() {
        return Map.of(
            "activeSubscriptions", clientSubscriptions.size(),
            "subscriptions", clientSubscriptions
        );
    }

    /**
     * 🔗 TRACK CLIENT SUBSCRIPTION
     */
    public void trackClientSubscription(String clientId) {
        clientSubscriptions.merge(clientId, 1, Integer::sum);
        logger.debug("📊 Client {} subscribed. Total subscriptions: {}", clientId, clientSubscriptions.get(clientId));
    }

    /**
     * 🔌 UNTRACK CLIENT SUBSCRIPTION
     */
    public void untrackClientSubscription(String clientId) {
        clientSubscriptions.computeIfPresent(clientId, (key, count) -> {
            int newCount = count - 1;
            logger.debug("📊 Client {} unsubscribed. Remaining subscriptions: {}", clientId, newCount);
            return newCount > 0 ? newCount : null;
        });
    }

    /**
     * 📊 BROADCAST RISK STATUS
     * 
     * Sends risk status updates to subscribed clients
     */
    public void broadcastRiskStatus(String clientId, Map<String, Object> statusData) {
        try {
            String topic = "/topic/risk-status/" + clientId;
            
            logger.debug("📊 Broadcasting risk status to topic: {}", topic);
            
            messagingTemplate.convertAndSend(topic, statusData);
            
        } catch (Exception e) {
            logger.error("❌ Error broadcasting risk status for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * 🔢 GET TOTAL ACTIVE CONNECTIONS
     */
    public int getTotalActiveConnections() {
        return clientSubscriptions.values().stream().mapToInt(Integer::intValue).sum();
    }
}
