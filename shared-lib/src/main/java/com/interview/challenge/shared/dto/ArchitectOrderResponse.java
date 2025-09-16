package com.interview.challenge.shared.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response from Architect API for order placement
 */
public class ArchitectOrderResponse {
    private String orderId;
    private String clientOrderId;
    private String symbol;
    private String side;
    private BigDecimal quantity;
    private BigDecimal orderQty;  // Quantity ordered
    private BigDecimal filledQty; // Quantity filled
    private String orderType;
    private String status;
    private BigDecimal price;
    private BigDecimal fillPrice;
    private BigDecimal averageFillPrice;
    private LocalDateTime timestamp;
    private LocalDateTime updatedAt;
    private String message;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
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

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getFillPrice() {
        return fillPrice;
    }

    public void setFillPrice(BigDecimal fillPrice) {
        this.fillPrice = fillPrice;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public BigDecimal getOrderQty() {
        return orderQty != null ? orderQty : quantity;
    }

    public void setOrderQty(BigDecimal orderQty) {
        this.orderQty = orderQty;
    }

    public BigDecimal getFilledQty() {
        return filledQty;
    }

    public void setFilledQty(BigDecimal filledQty) {
        this.filledQty = filledQty;
    }

    public BigDecimal getAverageFillPrice() {
        return averageFillPrice != null ? averageFillPrice : fillPrice;
    }

    public void setAverageFillPrice(BigDecimal averageFillPrice) {
        this.averageFillPrice = averageFillPrice;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt != null ? updatedAt : timestamp;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}