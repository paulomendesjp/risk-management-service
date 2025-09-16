package com.interview.challenge.shared.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of closing all positions for a client
 * Used by Risk Monitoring Service when risk limits are breached
 */
public class ClosePositionsResult {
    private String clientId;
    private String reason;
    private boolean success;
    private int positionsClosed;
    private int closedCount;  // Alias for positionsClosed
    private int failedCount;
    private String message;
    private String errorMessage;
    private BigDecimal totalValue;
    private List<String> closedPositions = new ArrayList<>();
    private List<String> failedOrderIds = new ArrayList<>();
    private int totalPositions;
    private LocalDateTime timestamp;

    public ClosePositionsResult() {
    }

    public ClosePositionsResult(boolean success, int positionsClosed, String message) {
        this.success = success;
        this.positionsClosed = positionsClosed;
        this.closedCount = positionsClosed;
        this.failedCount = 0;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getPositionsClosed() {
        return positionsClosed;
    }

    public void setPositionsClosed(int positionsClosed) {
        this.positionsClosed = positionsClosed;
        this.closedCount = positionsClosed;
    }

    // Compatibility methods
    public int getClosedCount() {
        return closedCount;
    }

    public void setClosedCount(int closedCount) {
        this.closedCount = closedCount;
        this.positionsClosed = closedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getClosedPositions() {
        return closedPositions;
    }

    public void setClosedPositions(List<String> closedPositions) {
        this.closedPositions = closedPositions;
    }

    public void addClosedPosition(String positionId) {
        this.closedPositions.add(positionId);
    }

    // Additional methods for position-service
    public boolean hasFailures() {
        return failedCount > 0 || !failedOrderIds.isEmpty();
    }

    public String getErrorMessage() {
        return errorMessage != null ? errorMessage : message;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

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

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public List<String> getFailedOrderIds() {
        return failedOrderIds;
    }

    public void setFailedOrderIds(List<String> failedOrderIds) {
        this.failedOrderIds = failedOrderIds;
    }

    public void addFailedOrder(String orderId) {
        this.failedOrderIds.add(orderId);
        this.failedCount++;
    }

    public void addClosedOrder(String orderId, BigDecimal value) {
        this.closedPositions.add(orderId);
        this.positionsClosed++;
        this.closedCount++;
        if (value != null) {
            this.totalValue = this.totalValue == null ? value : this.totalValue.add(value);
        }
    }

    public int getTotalPositions() {
        return totalPositions;
    }

    public void setTotalPositions(int totalPositions) {
        this.totalPositions = totalPositions;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    // Factory methods
    public static ClosePositionsResult success(String clientId, String reason) {
        ClosePositionsResult result = new ClosePositionsResult();
        result.setClientId(clientId);
        result.setReason(reason);
        result.setSuccess(true);
        result.setTimestamp(LocalDateTime.now());
        result.setTotalValue(BigDecimal.ZERO);
        return result;
    }

    public static ClosePositionsResult error(String clientId, String reason, String errorMessage) {
        ClosePositionsResult result = new ClosePositionsResult();
        result.setClientId(clientId);
        result.setReason(reason);
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        result.setMessage(errorMessage);
        result.setTimestamp(LocalDateTime.now());
        return result;
    }
}