package com.interview.challenge.shared.events;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event for notifications
 * According to requirements, notifications are sent via system logs (mandatory)
 * and optionally via Email, Slack, WebSocket
 */
public class NotificationEvent implements Serializable {

    private String clientId;
    private NotificationType type;
    private Channel channel;
    private String subject;
    private String message;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;

    public enum NotificationType {
        RISK_VIOLATION,
        POSITION_CLOSED,
        SYSTEM_ALERT,
        DAILY_RISK_TRIGGERED,
        MAX_RISK_TRIGGERED
    }

    public enum Channel {
        SYSTEM_LOG,    // Mandatory
        EMAIL,         // Optional
        SLACK,         // Optional
        WEBSOCKET      // Optional
    }

    public NotificationEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public NotificationEvent(String clientId, NotificationType type, Channel channel,
                           String subject, String message, LocalDateTime timestamp,
                           Map<String, Object> metadata) {
        this.clientId = clientId;
        this.type = type;
        this.channel = channel;
        this.subject = subject;
        this.message = message;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.metadata = metadata;
    }

    // Getters and Setters
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}