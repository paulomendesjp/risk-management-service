package com.interview.challenge.shared.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order data model based on the Python OrderData class
 * Represents trading order information
 */
@Document(collection = "orders")
public class OrderData {

    @Id
    private String id;

    @NotBlank(message = "Client ID is required")
    private String clientId;

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotBlank(message = "Action is required")
    private String action; // BUY, SELL

    @NotNull(message = "Order quantity is required")
    @Positive(message = "Order quantity must be positive")
    private BigDecimal orderQty;

    private String orderType = "MARKET"; // MARKET, LIMIT
    private BigDecimal limitPrice;
    private String strategy;

    // Status fields
    private String status; // PENDING, FILLED, PARTIALLY_FILLED, CANCELLED, REJECTED
    private BigDecimal filledQty;
    private BigDecimal avgFillPrice;

    // Risk management
    private BigDecimal stopLossPrice;
    private BigDecimal takeProfitPrice;
    private BigDecimal stopLoss; // Stop loss percentage or value
    private BigDecimal stopPrice; // Stop price for stop orders

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime filledAt;
    private LocalDateTime updatedAt;

    // Architect specific fields
    private String architectOrderId;
    private String errorMessage;

    // Constructor
    public OrderData() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public OrderData(String clientId, String symbol, String action, BigDecimal orderQty, String strategy) {
        this();
        this.clientId = clientId;
        this.symbol = symbol;
        this.action = action;
        this.orderQty = orderQty;
        this.strategy = strategy;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public BigDecimal getOrderQty() {
        return orderQty;
    }

    public void setOrderQty(BigDecimal orderQty) {
        this.orderQty = orderQty;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public void setLimitPrice(BigDecimal limitPrice) {
        this.limitPrice = limitPrice;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getFilledQty() {
        return filledQty;
    }

    public void setFilledQty(BigDecimal filledQty) {
        this.filledQty = filledQty;
    }

    public BigDecimal getAvgFillPrice() {
        return avgFillPrice;
    }

    public void setAvgFillPrice(BigDecimal avgFillPrice) {
        this.avgFillPrice = avgFillPrice;
    }

    public BigDecimal getStopLossPrice() {
        return stopLossPrice;
    }

    public void setStopLossPrice(BigDecimal stopLossPrice) {
        this.stopLossPrice = stopLossPrice;
    }

    public BigDecimal getTakeProfitPrice() {
        return takeProfitPrice;
    }

    public void setTakeProfitPrice(BigDecimal takeProfitPrice) {
        this.takeProfitPrice = takeProfitPrice;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getFilledAt() {
        return filledAt;
    }

    public void setFilledAt(LocalDateTime filledAt) {
        this.filledAt = filledAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getArchitectOrderId() {
        return architectOrderId;
    }

    public void setArchitectOrderId(String architectOrderId) {
        this.architectOrderId = architectOrderId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    // Utility methods
    public boolean isFilled() {
        return "FILLED".equalsIgnoreCase(status);
    }

    public boolean isPartiallyFilled() {
        return "PARTIALLY_FILLED".equalsIgnoreCase(status);
    }

    public boolean isPending() {
        return "PENDING".equalsIgnoreCase(status);
    }

    public boolean isCancelled() {
        return "CANCELLED".equalsIgnoreCase(status);
    }

    public boolean isRejected() {
        return "REJECTED".equalsIgnoreCase(status);
    }

    public boolean isActive() {
        return isPending() || isPartiallyFilled();
    }

    public void markAsFilled(BigDecimal filledQty, BigDecimal avgPrice) {
        this.status = "FILLED";
        this.filledQty = filledQty;
        this.avgFillPrice = avgPrice;
        this.filledAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsRejected(String errorMessage) {
        this.status = "REJECTED";
        this.errorMessage = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal getStopLoss() {
        return stopLoss;
    }

    public void setStopLoss(BigDecimal stopLoss) {
        this.stopLoss = stopLoss;
    }

    public BigDecimal getStopPrice() {
        return stopPrice;
    }

    public void setStopPrice(BigDecimal stopPrice) {
        this.stopPrice = stopPrice;
    }

    public boolean hasStopLoss() {
        return stopLoss != null && stopLoss.compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public String toString() {
        return "OrderData{" +
                "clientId='" + clientId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", action='" + action + '\'' +
                ", orderQty=" + orderQty +
                ", strategy='" + strategy + '\'' +
                '}';
    }
}