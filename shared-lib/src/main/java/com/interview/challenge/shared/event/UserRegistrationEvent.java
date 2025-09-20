package com.interview.challenge.shared.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * User Registration Event
 *
 * Published when a new user is registered or updated
 * Used by Risk Monitoring Service to start automatic monitoring
 */
public class UserRegistrationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventType; // "REGISTRATION", "UPDATE", "DELETION"
    private String clientId;
    private String exchange; // "KRAKEN", "BINANCE", etc.
    private BigDecimal initialBalance;
    private BigDecimal maxRiskValue;
    private String maxRiskType; // "percentage" or "absolute"
    private BigDecimal dailyRiskValue;
    private String dailyRiskType; // "percentage" or "absolute"
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    public UserRegistrationEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public UserRegistrationEvent(String eventType, String clientId, BigDecimal initialBalance) {
        this();
        this.eventType = eventType;
        this.clientId = clientId;
        this.initialBalance = initialBalance;
    }

    // Factory methods
    public static UserRegistrationEvent registration(String clientId, BigDecimal initialBalance) {
        return new UserRegistrationEvent("REGISTRATION", clientId, initialBalance);
    }

    public static UserRegistrationEvent update(String clientId) {
        return new UserRegistrationEvent("UPDATE", clientId, null);
    }

    public static UserRegistrationEvent deletion(String clientId) {
        return new UserRegistrationEvent("DELETION", clientId, null);
    }

    // Getters and Setters
    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }

    public BigDecimal getMaxRiskValue() {
        return maxRiskValue;
    }

    public void setMaxRiskValue(BigDecimal maxRiskValue) {
        this.maxRiskValue = maxRiskValue;
    }

    public String getMaxRiskType() {
        return maxRiskType;
    }

    public void setMaxRiskType(String maxRiskType) {
        this.maxRiskType = maxRiskType;
    }

    public BigDecimal getDailyRiskValue() {
        return dailyRiskValue;
    }

    public void setDailyRiskValue(BigDecimal dailyRiskValue) {
        this.dailyRiskValue = dailyRiskValue;
    }

    public String getDailyRiskType() {
        return dailyRiskType;
    }

    public void setDailyRiskType(String dailyRiskType) {
        this.dailyRiskType = dailyRiskType;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    @Override
    public String toString() {
        return "UserRegistrationEvent{" +
                "eventType='" + eventType + '\'' +
                ", clientId='" + clientId + '\'' +
                ", exchange='" + exchange + '\'' +
                ", initialBalance=" + initialBalance +
                ", timestamp=" + timestamp +
                '}';
    }
}