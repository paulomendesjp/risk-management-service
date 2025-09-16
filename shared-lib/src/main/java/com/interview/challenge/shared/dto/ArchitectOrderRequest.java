package com.interview.challenge.shared.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * DTO for Architect.co order request
 * Uses JsonProperty to match Python bridge expectations
 */
public class ArchitectOrderRequest {
    private String clientId;
    private String symbol;

    @JsonProperty("action")
    private String side; // BUY or SELL - mapped to "action" in JSON

    @JsonProperty("orderQty")
    private BigDecimal quantity; // mapped to "orderQty" in JSON

    @JsonProperty("orderType")
    private String type; // MARKET or LIMIT - mapped to "orderType" in JSON

    private BigDecimal price; // For limit orders
    private String timeInForce;

    // Constructors
    public ArchitectOrderRequest() {}

    public ArchitectOrderRequest(String symbol, String side, BigDecimal quantity, String type) {
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.type = type;
    }

    // Getters and Setters
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(String timeInForce) {
        this.timeInForce = timeInForce;
    }
}