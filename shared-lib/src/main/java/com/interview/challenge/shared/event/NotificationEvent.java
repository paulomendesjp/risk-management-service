package com.interview.challenge.shared.event;

import com.interview.challenge.shared.enums.NotificationPriority;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 📢 NOTIFICATION EVENT
 * 
 * Centralized notification event for all system notifications
 * Implements Requirement 5: Notifications
 * 
 * Event types:
 * - DAILY_RISK_TRIGGERED: Daily risk limit exceeded
 * - MAX_RISK_TRIGGERED: Maximum risk limit exceeded  
 * - BALANCE_UPDATE: Account balance changed
 * - MONITORING_ERROR: Error in monitoring system
 * - POSITION_CLOSED: Position was closed
 * - ACCOUNT_BLOCKED: Account was blocked
 * - SYSTEM_EVENT: General system event
 */
public class NotificationEvent {
    private String clientId;
    private NotificationPriority priority;
    private String message;
    private String action;
    private BigDecimal loss;
    private BigDecimal limit;
    private BigDecimal newBalance;
    private BigDecimal previousBalance;
    private String source;
    private String exchange;
    private LocalDateTime timestamp;
    private Map<String, Object> additionalData;
    private NotificationType eventType;
    private Map<String, Object> details;
    
    // Constructors
    public NotificationEvent() {
        this.timestamp = LocalDateTime.now();
    }
    
    public NotificationEvent(NotificationType eventType, String clientId, NotificationPriority priority, String message) {
        this.eventType = eventType;
        this.clientId = clientId;
        this.priority = priority;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
    
    // ========== FACTORY METHODS ==========
    
    /**
     * 🚨 MAX RISK TRIGGERED NOTIFICATION
     * 
     * Creates notification for maximum risk limit violation
     * Requirement 3: Max Risk Trigger
     * Requirement 5: Send final notification
     */
    public static NotificationEvent maxRiskTriggered(String clientId, BigDecimal loss, BigDecimal limit) {
        NotificationEvent event = new NotificationEvent();
        event.setEventType(NotificationType.MAX_RISK_TRIGGERED);
        event.setClientId(clientId);
        event.setPriority(NotificationPriority.CRITICAL);
        event.setLoss(loss);
        event.setLimit(limit);
        event.setMessage(String.format("MAX RISK LIMIT EXCEEDED for client %s: Loss $%s >= Limit $%s",
                                      clientId, loss, limit));
        event.setAction("All positions closed. Trading permanently disabled.");
        return event;
    }
    
    /**
     * ⚠️ DAILY RISK TRIGGERED NOTIFICATION
     * 
     * Creates notification for daily risk limit violation
     * Requirement 3: Daily Risk Trigger
     * Requirement 5: Send notification
     */
    public static NotificationEvent dailyRiskTriggered(String clientId, BigDecimal loss, BigDecimal limit) {
        NotificationEvent event = new NotificationEvent();
        event.setEventType(NotificationType.DAILY_RISK_TRIGGERED);
        event.setClientId(clientId);
        event.setPriority(NotificationPriority.HIGH);
        event.setLoss(loss);
        event.setLimit(limit);
        event.setMessage(String.format("DAILY RISK LIMIT EXCEEDED for client %s: Daily Loss $%s >= Limit $%s",
                                      clientId, loss, limit));
        event.setAction("All positions closed. Trading disabled for today.");
        return event;
    }
    
    /**
     * 💰 BALANCE UPDATE NOTIFICATION
     * 
     * Creates notification for balance changes (info level)
     */
    public static NotificationEvent balanceUpdate(String clientId, BigDecimal newBalance,
                                                BigDecimal previousBalance, String source) {
        NotificationEvent event = new NotificationEvent();
        event.setEventType(NotificationType.BALANCE_UPDATE);
        event.setClientId(clientId);
        event.setPriority(NotificationPriority.LOW);
        event.setNewBalance(newBalance);
        event.setPreviousBalance(previousBalance);
        event.setSource(source);
        event.setMessage(String.format("Balance updated for client %s: $%s -> $%s (source: %s)",
                                      clientId, previousBalance, newBalance, source));
        return event;
    }
    
    /**
     * 🚨 MONITORING ERROR NOTIFICATION
     * 
     * Creates notification for monitoring system errors
     */
    public static NotificationEvent monitoringError(String clientId, String errorMessage) {
        NotificationEvent event = new NotificationEvent();
        event.setEventType(NotificationType.MONITORING_ERROR);
        event.setClientId(clientId);
        event.setPriority(NotificationPriority.HIGH);
        event.setMessage(String.format("Monitoring error for client %s: %s", clientId, errorMessage));
        return event;
    }
    
    /**
     * 📊 POSITION CLOSED NOTIFICATION
     * 
     * Creates notification for position closure events
     */
    public static NotificationEvent positionClosed(String clientId, String reason, int closedCount, int failedCount) {
        NotificationEvent event = new NotificationEvent();
        event.setEventType(NotificationType.POSITION_CLOSED);
        event.setClientId(clientId);
        event.setPriority(NotificationPriority.NORMAL);
        event.setMessage(String.format("Positions closed for client %s: %d closed, %d failed. Reason: %s",
                                      clientId, closedCount, failedCount, reason));
        event.setAction(String.format("%d positions closed, %d failed", closedCount, failedCount));
        return event;
    }
    
    /**
     * 🔒 ACCOUNT BLOCKED NOTIFICATION
     * 
     * Creates notification for account blocking events
     */
    public static NotificationEvent accountBlocked(String clientId, String blockType, String reason) {
        NotificationEvent event = new NotificationEvent();
        event.setEventType(NotificationType.ACCOUNT_BLOCKED);
        event.setClientId(clientId);
        event.setPriority("PERMANENT".equals(blockType) ? NotificationPriority.CRITICAL : NotificationPriority.HIGH);
        event.setMessage(String.format("Account %s blocked for client %s: %s", blockType, clientId, reason));
        event.setAction(String.format("Account blocked: %s", blockType));
        return event;
    }
    
    /**
     * 🔧 SYSTEM EVENT NOTIFICATION
     * 
     * Creates notification for general system events
     */
    public static NotificationEvent systemEvent(String eventType, String message) {
        NotificationEvent event = new NotificationEvent();
        event.setEventType(NotificationType.SYSTEM_EVENT);
        event.setPriority(NotificationPriority.NORMAL);
        event.setMessage(message);
        return event;
    }

    /**
     * Builder pattern for creating notification events
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private NotificationEvent event = new NotificationEvent();

        public Builder clientId(String clientId) {
            event.setClientId(clientId);
            return this;
        }

        public Builder eventType(NotificationType eventType) {
            event.setEventType(eventType);
            return this;
        }

        public Builder exchange(String exchange) {
            event.setExchange(exchange);
            return this;
        }

        public Builder details(Map<String, Object> details) {
            event.setDetails(details);
            return this;
        }

        public Builder message(String message) {
            event.setMessage(message);
            return this;
        }

        public Builder priority(NotificationPriority priority) {
            event.setPriority(priority);
            return this;
        }

        public Builder previousBalance(BigDecimal previousBalance) {
            event.setPreviousBalance(previousBalance);
            return this;
        }

        public Builder newBalance(BigDecimal newBalance) {
            event.setNewBalance(newBalance);
            return this;
        }

        public Builder loss(BigDecimal loss) {
            event.setLoss(loss);
            return this;
        }

        public Builder limit(BigDecimal limit) {
            event.setLimit(limit);
            return this;
        }

        public Builder source(String source) {
            event.setSource(source);
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            event.setTimestamp(timestamp);
            return this;
        }

        public Builder action(String action) {
            event.setAction(action);
            return this;
        }

        public NotificationEvent build() {
            return event;
        }
    }

    /**
     * Factory method for order placed notifications
     */
    public static NotificationEvent orderPlaced(String clientId, String symbol, String side, BigDecimal quantity, String orderId) {
        NotificationEvent event = new NotificationEvent();
        event.setClientId(clientId);
        event.setMessage(String.format("Order placed: %s %s %s, Order ID: %s", side, quantity, symbol, orderId));
        return event;
    }

    /**
     * Factory method for positions closed notifications
     */
    public static NotificationEvent positionsClosed(String clientId, String details) {
        NotificationEvent event = new NotificationEvent();
        event.setClientId(clientId);
        event.setMessage(String.format("Positions closed for client %s: %s", clientId, details));
        return event;
    }
    
    // ========== GETTERS AND SETTERS ==========
    
    
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    
    public NotificationPriority getPriority() { return priority; }
    public void setPriority(NotificationPriority priority) { this.priority = priority; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    public BigDecimal getLoss() { return loss; }
    public void setLoss(BigDecimal loss) { this.loss = loss; }
    
    public BigDecimal getLimit() { return limit; }
    public void setLimit(BigDecimal limit) { this.limit = limit; }
    
    public BigDecimal getNewBalance() { return newBalance; }
    public void setNewBalance(BigDecimal newBalance) { this.newBalance = newBalance; }
    
    public BigDecimal getPreviousBalance() { return previousBalance; }
    public void setPreviousBalance(BigDecimal previousBalance) { this.previousBalance = previousBalance; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public Map<String, Object> getAdditionalData() { return additionalData; }
    public void setAdditionalData(Map<String, Object> additionalData) { this.additionalData = additionalData; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public NotificationType getEventType() { return eventType; }
    public void setEventType(NotificationType eventType) { this.eventType = eventType; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
    
    @Override
    public String toString() {
        return String.format("NotificationEvent{eventType='%s', clientId='%s', priority='%s', message='%s', timestamp=%s}",
                           eventType, clientId, priority, message, timestamp);
    }
}
