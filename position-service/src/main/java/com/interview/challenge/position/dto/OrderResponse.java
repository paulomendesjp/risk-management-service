package com.interview.challenge.position.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Structured response for order placement operations
 * No more manual Map manipulation
 */
public class OrderResponse {
    
    private String tradeId;
    private String clientId;
    private String symbol;
    private String action;
    private BigDecimal quantity;
    private BigDecimal price;
    private String orderMethod;
    private String stopOrderId;
    private LocalDateTime executionTime;
    private boolean success;
    private String errorMessage;
    private String requestId;
    
    // Constructors
    public OrderResponse() {}
    
    public OrderResponse(String tradeId, String clientId, String symbol) {
        this.tradeId = tradeId;
        this.clientId = clientId;
        this.symbol = symbol;
        this.success = true;
        this.executionTime = LocalDateTime.now();
        this.requestId = java.util.UUID.randomUUID().toString();
    }
    
    // Static factory methods
    public static OrderResponse success(String tradeId, String clientId, String symbol, BigDecimal price) {
        OrderResponse response = new OrderResponse(tradeId, clientId, symbol);
        response.setPrice(price);
        return response;
    }
    
    public static OrderResponse error(String clientId, String symbol, String errorMessage) {
        OrderResponse response = new OrderResponse(null, clientId, symbol);
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }
    
    // Getters and Setters
    public String getTradeId() {
        return tradeId;
    }
    
    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
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
    
    public BigDecimal getQuantity() {
        return quantity;
    }
    
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    
    public String getOrderMethod() {
        return orderMethod;
    }
    
    public void setOrderMethod(String orderMethod) {
        this.orderMethod = orderMethod;
    }
    
    public String getStopOrderId() {
        return stopOrderId;
    }
    
    public void setStopOrderId(String stopOrderId) {
        this.stopOrderId = stopOrderId;
    }
    
    public LocalDateTime getExecutionTime() {
        return executionTime;
    }
    
    public void setExecutionTime(LocalDateTime executionTime) {
        this.executionTime = executionTime;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    @Override
    public String toString() {
        return "OrderResponse{" +
                "tradeId='" + tradeId + '\'' +
                ", clientId='" + clientId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", success=" + success +
                ", price=" + price +
                '}';
    }
}

