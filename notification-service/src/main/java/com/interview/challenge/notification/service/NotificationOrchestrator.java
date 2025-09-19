package com.interview.challenge.notification.service;

import com.interview.challenge.notification.handler.EventLogHandlerRegistry;
import com.interview.challenge.shared.event.NotificationEvent;
import com.interview.challenge.shared.event.NotificationType;
import com.interview.challenge.shared.enums.NotificationPriority;
import com.interview.challenge.shared.audit.MandatoryAuditLogger;
import com.interview.challenge.notification.model.NotificationHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 📢 NOTIFICATION ORCHESTRATOR
 * 
 * Centralized notification service that handles ALL notifications
 * Implements Requirement 5: Notifications
 * 
 * Responsibilities:
 * - Receive notification events from RabbitMQ queue
 * - Execute MANDATORY system logs (always logged)
 * - Route to optional notification channels (email, Slack, WebSocket)
 * - Store notification history in MongoDB
 * - Handle notification failures gracefully
 * 
 * Architecture:
 * Risk Service -> RabbitMQ Queue -> Notification Service -> Multiple Channels
 * This ensures risk service doesn't do direct logging, centralizing all notifications
 */
@Service
public class NotificationOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(NotificationOrchestrator.class);

    @Autowired
    private MandatoryAuditLogger mandatoryAuditLogger;

    @Autowired
    private EventLogHandlerRegistry eventLogHandlerRegistry;

    @Autowired
    private EmailNotificationService emailService;
    
    @Autowired
    private SlackNotificationService slackService;
    
    @Autowired
    private WebSocketNotificationService webSocketService;
    
    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 📥 MAIN NOTIFICATION QUEUE LISTENER
     * 
     * Listens to "notifications" queue from RiskMonitoringService
     * Processes ALL notification events centrally
     */
    @RabbitListener(queues = "notifications")
    public void handleNotificationEvent(NotificationEvent event) {
        try {
            logger.info("📥 Received notification event: {} for client {}", 
                       event.getEventType(), event.getClientId());
            
            // 1. MANDATORY SYSTEM LOGS (Requirement 5: Always logged)
            logMandatorySystemEvent(event);
            
            // 2. Store notification history
            storeNotificationHistory(event);
            
            // 3. Route to optional notification channels based on priority
            routeToNotificationChannels(event);
            
            logger.debug("✅ Notification event processed successfully: {}", event.getEventType());
            
        } catch (Exception e) {
            logger.error("❌ Error processing notification event {}: {}", 
                        event.getEventType(), e.getMessage(), e);
            
            // Fallback: At least log the critical information
            mandatoryAuditLogger.logSystemEvent("NOTIFICATION_PROCESSING_ERROR", 
                "Failed to process " + event.getEventType() + " for client " + 
                event.getClientId() + ": " + e.getMessage());
        }
    }

    /**
     * 📝 MANDATORY SYSTEM LOGS
     *
     * Implements Requirement 5: "Notify via system logs (mandatory)"
     * These logs are ALWAYS written regardless of other notification failures
     * Uses EventLogHandlerRegistry for cleaner code without switch/case
     */
    private void logMandatorySystemEvent(NotificationEvent event) {
        try {
            eventLogHandlerRegistry.handleEvent(event);
        } catch (Exception e) {
            logger.error("❌ Error writing mandatory logs for event {}: {}",
                        event.getEventType(), e.getMessage());

            // Last resort: Use basic logger
            logger.error("MANDATORY_LOG_FAILED: {} - {}", event.getEventType(), event.getMessage());
        }
    }

    /**
     * 🗄️ STORE NOTIFICATION HISTORY
     * 
     * Store all notifications in MongoDB for auditing and tracking
     */
    private void storeNotificationHistory(NotificationEvent event) {
        try {
            NotificationHistory history = new NotificationHistory();
            history.setEventType(event.getEventType().toString());
            history.setClientId(event.getClientId());
            history.setPriority(event.getPriority().toString());
            history.setMessage(event.getMessage());
            history.setAction(event.getAction());
            history.setLoss(event.getLoss());
            history.setLimit(event.getLimit());
            history.setNewBalance(event.getNewBalance());
            history.setPreviousBalance(event.getPreviousBalance());
            history.setSource(event.getSource());
            history.setExchange(event.getExchange());
            history.setTimestamp(event.getTimestamp());
            history.setCreatedAt(LocalDateTime.now());
            
            mongoTemplate.save(history);
            
            logger.debug("💾 Notification history stored for event: {}", event.getEventType());
            
        } catch (Exception e) {
            logger.error("❌ Error storing notification history for event {}: {}", 
                        event.getEventType(), e.getMessage());
        }
    }

    /**
     * 🚀 ROUTE TO NOTIFICATION CHANNELS
     * 
     * Route notifications to optional channels based on priority and type
     * Requirement 5: "Optional: Email, Slack, WebSocket push"
     */
    private void routeToNotificationChannels(NotificationEvent event) {
        try {
            NotificationPriority priority = event.getPriority();
            NotificationType eventType = event.getEventType();
            
            // Always send to WebSocket for real-time UI updates
            sendWebSocketNotification(event);
            
            // Send email for high priority events
            if (priority == NotificationPriority.CRITICAL ||
                priority == NotificationPriority.HIGH) {
                
                sendEmailNotification(event);
            }
            
            // Send Slack for critical events and risk violations
            if (priority == NotificationPriority.CRITICAL ||
                eventType == NotificationType.MAX_RISK_TRIGGERED ||
                eventType == NotificationType.DAILY_RISK_TRIGGERED) {
                
                sendSlackNotification(event);
            }
            
        } catch (Exception e) {
            logger.error("❌ Error routing notification to channels for event {}: {}", 
                        event.getEventType(), e.getMessage());
        }
    }

    /**
     * 📧 SEND EMAIL NOTIFICATION
     */
    private void sendEmailNotification(NotificationEvent event) {
        try {
            emailService.send(event);
            logger.debug("📧 Email notification sent for event: {}", event.getEventType());
        } catch (Exception e) {
            logger.warn("⚠️ Email notification failed for event {}: {}",
                       event.getEventType(), e.getMessage());
        }
    }

    /**
     * 💬 SEND SLACK NOTIFICATION
     */
    private void sendSlackNotification(NotificationEvent event) {
        try {
            slackService.sendNotification(event);
            logger.debug("💬 Slack notification sent for event: {}", event.getEventType());
        } catch (Exception e) {
            logger.warn("⚠️ Slack notification failed for event {}: {}", 
                       event.getEventType(), e.getMessage());
        }
    }

    /**
     * 🌐 SEND WEBSOCKET NOTIFICATION
     */
    private void sendWebSocketNotification(NotificationEvent event) {
        try {
            webSocketService.sendNotification(event);
            logger.debug("🌐 WebSocket notification sent for event: {}", event.getEventType());
        } catch (Exception e) {
            logger.warn("⚠️ WebSocket notification failed for event {}: {}", 
                       event.getEventType(), e.getMessage());
        }
    }

    /**
     * 📊 GET NOTIFICATION STATISTICS
     */
    public NotificationStatistics getStatistics() {
        try {
            long totalNotifications = mongoTemplate.count(
                new org.springframework.data.mongodb.core.query.Query(), 
                NotificationHistory.class
            );
            
            long criticalNotifications = mongoTemplate.count(
                org.springframework.data.mongodb.core.query.Query.query(
                    org.springframework.data.mongodb.core.query.Criteria.where("priority")
                        .is(NotificationPriority.CRITICAL.toString())
                ), 
                NotificationHistory.class
            );
            
            long todayNotifications = mongoTemplate.count(
                org.springframework.data.mongodb.core.query.Query.query(
                    org.springframework.data.mongodb.core.query.Criteria.where("createdAt")
                        .gte(LocalDateTime.now().toLocalDate().atStartOfDay())
                ), 
                NotificationHistory.class
            );
            
            return new NotificationStatistics(totalNotifications, criticalNotifications, todayNotifications);
            
        } catch (Exception e) {
            logger.error("❌ Error getting notification statistics: {}", e.getMessage());
            return new NotificationStatistics(0, 0, 0);
        }
    }

    /**
     * 📊 NOTIFICATION STATISTICS INNER CLASS
     */
    public static class NotificationStatistics {
        private final long totalNotifications;
        private final long criticalNotifications;
        private final long todayNotifications;
        
        public NotificationStatistics(long total, long critical, long today) {
            this.totalNotifications = total;
            this.criticalNotifications = critical;
            this.todayNotifications = today;
        }
        
        public long getTotalNotifications() { return totalNotifications; }
        public long getCriticalNotifications() { return criticalNotifications; }
        public long getTodayNotifications() { return todayNotifications; }
    }
}
