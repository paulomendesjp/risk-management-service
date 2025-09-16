package com.interview.challenge.shared.events;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published when positions are closed
 */
public class PositionCloseEvent implements Serializable {

    private String clientId;
    private String apiKey;
    private String apiSecret;
    private String reason;
    private CloseReason closeReason;
    private int positionsClosed;
    private BigDecimal totalValue;
    private LocalDateTime timestamp;
    private String requestId;
    private boolean success;

    /**
     * Reason for closing positions
     */
    public enum CloseReason {
        DAILY_RISK_EXCEEDED,
        MAX_RISK_EXCEEDED,
        MANUAL_REQUEST,
        EMERGENCY_STOP
    }

    public PositionCloseEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public PositionCloseEvent(String clientId, String apiKey, String apiSecret,
                             CloseReason closeReason, LocalDateTime timestamp, String requestId) {
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.closeReason = closeReason;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.requestId = requestId;
    }

    public PositionCloseEvent(String clientId, String reason, int positionsClosed,
                             BigDecimal totalValue, boolean success) {
        this.clientId = clientId;
        this.reason = reason;
        this.positionsClosed = positionsClosed;
        this.totalValue = totalValue;
        this.success = success;
        this.timestamp = LocalDateTime.now();
    }

    // Status enum for event type
    public enum Status {
        COMPLETED,
        PARTIAL,
        FAILED
    }

    public Status getStatus() {
        if (!success) return Status.FAILED;
        return positionsClosed > 0 ? Status.COMPLETED : Status.PARTIAL;
    }

    // Getters and Setters
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public int getPositionsClosed() {
        return positionsClosed;
    }

    public void setPositionsClosed(int positionsClosed) {
        this.positionsClosed = positionsClosed;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}