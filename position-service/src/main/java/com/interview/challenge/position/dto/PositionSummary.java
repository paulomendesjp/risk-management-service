package com.interview.challenge.position.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Summary of positions for a client
 */
public class PositionSummary {

    private String clientId;
    private int totalPositions;
    private BigDecimal totalValue;
    private BigDecimal totalLoss;
    private BigDecimal totalProfit;
    private LocalDateTime timestamp;

    public PositionSummary() {
        this.timestamp = LocalDateTime.now();
    }

    public PositionSummary(String clientId, int totalPositions, BigDecimal totalValue, BigDecimal totalLoss) {
        this.clientId = clientId;
        this.totalPositions = totalPositions;
        this.totalValue = totalValue;
        this.totalLoss = totalLoss;
        this.totalProfit = totalValue.subtract(totalLoss);
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public int getTotalPositions() {
        return totalPositions;
    }

    public void setTotalPositions(int totalPositions) {
        this.totalPositions = totalPositions;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public BigDecimal getTotalLoss() {
        return totalLoss;
    }

    public void setTotalLoss(BigDecimal totalLoss) {
        this.totalLoss = totalLoss;
    }

    public BigDecimal getTotalProfit() {
        return totalProfit;
    }

    public void setTotalProfit(BigDecimal totalProfit) {
        this.totalProfit = totalProfit;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean hasLoss() {
        return totalLoss != null && totalLoss.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasProfit() {
        return totalProfit != null && totalProfit.compareTo(BigDecimal.ZERO) > 0;
    }
}