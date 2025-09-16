package com.interview.challenge.shared.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response from Architect API for position information
 */
public class ArchitectPositionResponse {
    private String positionId;
    private String symbol;
    private String side;
    private BigDecimal quantity;
    private BigDecimal averagePrice;
    private BigDecimal currentPrice;
    private BigDecimal unrealizedPnl;
    private BigDecimal realizedPnl;
    private LocalDateTime openTime;
    private String status;

    public String getPositionId() {
        return positionId;
    }

    public void setPositionId(String positionId) {
        this.positionId = positionId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAveragePrice() {
        return averagePrice;
    }

    public void setAveragePrice(BigDecimal averagePrice) {
        this.averagePrice = averagePrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getUnrealizedPnl() {
        return unrealizedPnl;
    }

    public void setUnrealizedPnl(BigDecimal unrealizedPnl) {
        this.unrealizedPnl = unrealizedPnl;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public void setRealizedPnl(BigDecimal realizedPnl) {
        this.realizedPnl = realizedPnl;
    }

    public LocalDateTime getOpenTime() {
        return openTime;
    }

    public void setOpenTime(LocalDateTime openTime) {
        this.openTime = openTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Check if position has a loss
     */
    public boolean hasLoss() {
        return unrealizedPnl != null && unrealizedPnl.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Get the loss amount (positive value)
     */
    public BigDecimal getLossAmount() {
        if (hasLoss()) {
            return unrealizedPnl.abs();
        }
        return BigDecimal.ZERO;
    }

    /**
     * Calculate position value
     */
    public BigDecimal getPositionValue() {
        if (quantity != null && currentPrice != null) {
            return quantity.multiply(currentPrice);
        }
        return BigDecimal.ZERO;
    }
}