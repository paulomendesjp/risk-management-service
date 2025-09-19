package com.interview.challenge.notification.service;

import com.interview.challenge.shared.event.NotificationEvent;
import com.interview.challenge.shared.event.NotificationType;
import com.interview.challenge.shared.enums.NotificationPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 🌐 WEBSOCKET NOTIFICATION SERVICE
 *
 * Handles WebSocket notifications for real-time UI updates
 * Implements Requirement 5: "Optional: WebSocket push notifications"
 */
@Service
public class WebSocketNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketNotificationService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Send WebSocket notification for real-time UI updates
     */
    public void sendNotification(NotificationEvent event) {
        try {
            // Send to global notification topic
            sendGlobalNotification(event);

            // Send to client-specific topic if clientId is present
            if (event.getClientId() != null) {
                sendClientSpecificNotification(event);
            }

            logger.debug("🌐 WebSocket notification sent successfully for event: {} (client: {})",
                        event.getEventType(), event.getClientId());

        } catch (Exception e) {
            logger.error("❌ Failed to send WebSocket notification for event {}: {}",
                        event.getEventType(), e.getMessage());
            throw new RuntimeException("WebSocket notification failed", e);
        }
    }

    /**
     * Send notification to global topic for admin dashboards
     */
    private void sendGlobalNotification(NotificationEvent event) {
        try {
            Map<String, Object> notification = buildNotificationPayload(event);

            // Send to global notifications topic
            messagingTemplate.convertAndSend("/topic/notifications", notification);

            // Send to specific event type topic
            String eventTopic = "/topic/notifications/" + event.getEventType().name().toLowerCase();
            messagingTemplate.convertAndSend(eventTopic, notification);

        } catch (Exception e) {
            logger.error("❌ Error sending global WebSocket notification: {}", e.getMessage());
        }
    }

    /**
     * Send notification to client-specific topic for user dashboards
     */
    private void sendClientSpecificNotification(NotificationEvent event) {
        try {
            Map<String, Object> notification = buildNotificationPayload(event);

            // Send to client-specific topic
            String clientTopic = "/topic/client/" + event.getClientId() + "/notifications";
            messagingTemplate.convertAndSend(clientTopic, notification);

            // Send risk-specific notifications to risk topic
            if (isRiskEvent(event.getEventType())) {
                String riskTopic = "/topic/client/" + event.getClientId() + "/risk";
                messagingTemplate.convertAndSend(riskTopic, notification);
            }

        } catch (Exception e) {
            logger.error("❌ Error sending client-specific WebSocket notification: {}", e.getMessage());
        }
    }

    /**
     * Build WebSocket notification payload
     */
    private Map<String, Object> buildNotificationPayload(NotificationEvent event) {
        Map<String, Object> payload = new HashMap<>();

        payload.put("eventType", event.getEventType());
        payload.put("clientId", event.getClientId());
        payload.put("priority", event.getPriority());
        payload.put("message", event.getMessage());
        payload.put("action", event.getAction());
        payload.put("timestamp", event.getTimestamp());

        // Add event-specific data
        switch (event.getEventType()) {
            case MAX_RISK_TRIGGERED:
            case DAILY_RISK_TRIGGERED:
                payload.put("loss", event.getLoss());
                payload.put("limit", event.getLimit());
                payload.put("riskType", event.getEventType());
                break;

            case BALANCE_UPDATE:
                payload.put("newBalance", event.getNewBalance());
                payload.put("previousBalance", event.getPreviousBalance());
                payload.put("source", event.getSource());
                break;
        }

        // Add UI-specific metadata
        payload.put("ui", buildUIMetadata(event));

        return payload;
    }

    /**
     * Build UI-specific metadata for frontend rendering
     */
    private Map<String, Object> buildUIMetadata(NotificationEvent event) {
        Map<String, Object> ui = new HashMap<>();

        ui.put("icon", getIconForEventType(event.getEventType()));
        ui.put("color", getColorForPriority(event.getPriority()));
        ui.put("autoHide", shouldAutoHide(event.getPriority()));
        ui.put("sound", shouldPlaySound(event.getPriority()));

        return ui;
    }

    /**
     * Get icon for event type
     */
    private String getIconForEventType(NotificationType eventType) {
        switch (eventType) {
            case MAX_RISK_TRIGGERED:
                return "error";
            case DAILY_RISK_TRIGGERED:
                return "warning";
            case BALANCE_UPDATE:
                return "account_balance";
            case MONITORING_ERROR:
                return "bug_report";
            case POSITION_CLOSED:
                return "close";
            case ACCOUNT_BLOCKED:
                return "block";
            case SYSTEM_EVENT:
                return "settings";
            default:
                return "info";
        }
    }

    /**
     * Get color for priority level
     */
    private String getColorForPriority(NotificationPriority priority) {
        switch (priority) {
            case CRITICAL:
                return "error";
            case HIGH:
                return "warning";
            case NORMAL:
                return "info";
            case LOW:
                return "success";
            default:
                return "default";
        }
    }

    /**
     * Determine if notification should auto-hide
     */
    private boolean shouldAutoHide(NotificationPriority priority) {
        return priority != NotificationPriority.CRITICAL &&
               priority != NotificationPriority.HIGH;
    }

    /**
     * Determine if notification should play sound
     */
    private boolean shouldPlaySound(NotificationPriority priority) {
        return priority == NotificationPriority.CRITICAL ||
               priority == NotificationPriority.HIGH;
    }

    /**
     * Check if event is risk-related
     */
    private boolean isRiskEvent(NotificationType eventType) {
        return eventType == NotificationType.MAX_RISK_TRIGGERED ||
               eventType == NotificationType.DAILY_RISK_TRIGGERED ||
               eventType == NotificationType.MONITORING_ERROR;
    }
}
