package com.interview.challenge.notification.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 🗄️ NOTIFICATION HISTORY
 * 
 * MongoDB document to store notification history for auditing and tracking
 * Provides complete audit trail of all notifications sent through the system
 */
@Document(collection = "notification_history")
public class NotificationHistory {

    @Id
    private String id;
    
    @Indexed
    private String eventType;
    
    @Indexed
    private String clientId;
    
    private String priority;
    private String message;
    private String action;
    
    // Risk-related fields
    private BigDecimal loss;
    private BigDecimal limit;
    
    // Balance-related fields
    private BigDecimal newBalance;
    private BigDecimal previousBalance;
    private String source;

    // Exchange identification
    @Indexed
    private String exchange;
    
    // Timestamps
    @Indexed
    private LocalDateTime timestamp;  // Original event timestamp
    
    @Indexed
    private LocalDateTime createdAt;  // When stored in notification service
    
    // Notification delivery tracking
    private boolean emailSent = false;
    private boolean slackSent = false;
    private boolean webSocketSent = false;
    private LocalDateTime emailSentAt;
    private LocalDateTime slackSentAt;
    private LocalDateTime webSocketSentAt;
    
    // Error tracking
    private String emailError;
    private String slackError;
    private String webSocketError;

    // Constructors
    public NotificationHistory() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

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

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isEmailSent() { return emailSent; }
    public void setEmailSent(boolean emailSent) { this.emailSent = emailSent; }

    public boolean isSlackSent() { return slackSent; }
    public void setSlackSent(boolean slackSent) { this.slackSent = slackSent; }

    public boolean isWebSocketSent() { return webSocketSent; }
    public void setWebSocketSent(boolean webSocketSent) { this.webSocketSent = webSocketSent; }

    public LocalDateTime getEmailSentAt() { return emailSentAt; }
    public void setEmailSentAt(LocalDateTime emailSentAt) { this.emailSentAt = emailSentAt; }

    public LocalDateTime getSlackSentAt() { return slackSentAt; }
    public void setSlackSentAt(LocalDateTime slackSentAt) { this.slackSentAt = slackSentAt; }

    public LocalDateTime getWebSocketSentAt() { return webSocketSentAt; }
    public void setWebSocketSentAt(LocalDateTime webSocketSentAt) { this.webSocketSentAt = webSocketSentAt; }

    public String getEmailError() { return emailError; }
    public void setEmailError(String emailError) { this.emailError = emailError; }

    public String getSlackError() { return slackError; }
    public void setSlackError(String slackError) { this.slackError = slackError; }

    public String getWebSocketError() { return webSocketError; }
    public void setWebSocketError(String webSocketError) { this.webSocketError = webSocketError; }

    /**
     * Mark email as successfully sent
     */
    public void markEmailSent() {
        this.emailSent = true;
        this.emailSentAt = LocalDateTime.now();
        this.emailError = null;
    }

    /**
     * Mark email as failed with error
     */
    public void markEmailFailed(String error) {
        this.emailSent = false;
        this.emailError = error;
    }

    /**
     * Mark Slack as successfully sent
     */
    public void markSlackSent() {
        this.slackSent = true;
        this.slackSentAt = LocalDateTime.now();
        this.slackError = null;
    }

    /**
     * Mark Slack as failed with error
     */
    public void markSlackFailed(String error) {
        this.slackSent = false;
        this.slackError = error;
    }

    /**
     * Mark WebSocket as successfully sent
     */
    public void markWebSocketSent() {
        this.webSocketSent = true;
        this.webSocketSentAt = LocalDateTime.now();
        this.webSocketError = null;
    }

    /**
     * Mark WebSocket as failed with error
     */
    public void markWebSocketFailed(String error) {
        this.webSocketSent = false;
        this.webSocketError = error;
    }

    @Override
    public String toString() {
        return String.format("NotificationHistory{id='%s', eventType='%s', clientId='%s', priority='%s', timestamp=%s}",
                           id, eventType, clientId, priority, timestamp);
    }
}

