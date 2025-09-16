package com.interview.challenge.notification.dto;

import com.interview.challenge.notification.enums.NotificationEventType;
import com.interview.challenge.notification.enums.NotificationPriority;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for notification requests
 */
public class NotificationRequestDTO {

    private NotificationEventType eventType;
    private NotificationPriority priority;
    private String clientId;
    private String message;
    private String action;
    private BigDecimal loss;
    private BigDecimal limit;
    private BigDecimal newBalance;
    private BigDecimal previousBalance;
    private String source;
    private LocalDateTime timestamp;

    // Builder pattern for clean object creation
    public static class Builder {
        private NotificationRequestDTO dto;

        public Builder() {
            dto = new NotificationRequestDTO();
            dto.timestamp = LocalDateTime.now();
        }

        public Builder eventType(NotificationEventType eventType) {
            dto.eventType = eventType;
            return this;
        }

        public Builder priority(NotificationPriority priority) {
            dto.priority = priority;
            return this;
        }

        public Builder clientId(String clientId) {
            dto.clientId = clientId;
            return this;
        }

        public Builder message(String message) {
            dto.message = message;
            return this;
        }

        public Builder action(String action) {
            dto.action = action;
            return this;
        }

        public Builder loss(BigDecimal loss) {
            dto.loss = loss;
            return this;
        }

        public Builder limit(BigDecimal limit) {
            dto.limit = limit;
            return this;
        }

        public Builder balances(BigDecimal previousBalance, BigDecimal newBalance) {
            dto.previousBalance = previousBalance;
            dto.newBalance = newBalance;
            return this;
        }

        public Builder source(String source) {
            dto.source = source;
            return this;
        }

        public NotificationRequestDTO build() {
            return dto;
        }
    }

    // Getters
    public NotificationEventType getEventType() { return eventType; }
    public NotificationPriority getPriority() { return priority; }
    public String getClientId() { return clientId; }
    public String getMessage() { return message; }
    public String getAction() { return action; }
    public BigDecimal getLoss() { return loss; }
    public BigDecimal getLimit() { return limit; }
    public BigDecimal getNewBalance() { return newBalance; }
    public BigDecimal getPreviousBalance() { return previousBalance; }
    public String getSource() { return source; }
    public LocalDateTime getTimestamp() { return timestamp; }

    // Setters
    public void setEventType(NotificationEventType eventType) { this.eventType = eventType; }
    public void setPriority(NotificationPriority priority) { this.priority = priority; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public void setMessage(String message) { this.message = message; }
    public void setAction(String action) { this.action = action; }
    public void setLoss(BigDecimal loss) { this.loss = loss; }
    public void setLimit(BigDecimal limit) { this.limit = limit; }
    public void setNewBalance(BigDecimal newBalance) { this.newBalance = newBalance; }
    public void setPreviousBalance(BigDecimal previousBalance) { this.previousBalance = previousBalance; }
    public void setSource(String source) { this.source = source; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}