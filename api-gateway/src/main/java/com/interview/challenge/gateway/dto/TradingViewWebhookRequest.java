package com.interview.challenge.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO for TradingView webhook request
 * Matches the format that TradingView sends
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradingViewWebhookRequest {

    @JsonProperty("clientId")
    private String clientId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("action")
    private String action; // BUY or SELL

    @JsonProperty("orderQty")
    private BigDecimal orderQty;

    @JsonProperty("orderType")
    private String orderType = "MARKET"; // MARKET or LIMIT

    @JsonProperty("limitPrice")
    private BigDecimal limitPrice;

    @JsonProperty("stopLoss")
    private BigDecimal stopLoss;

    @JsonProperty("takeProfit")
    private BigDecimal takeProfit;

    @JsonProperty("strategy")
    private String strategy;

    @JsonProperty("venue")
    private String venue; // CME, NYSE, etc.

    @JsonProperty("inverse")
    private Boolean inverse = false;

    @JsonProperty("pyramid")
    private Boolean pyramid = false;

    // Additional fields from TradingView alerts
    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("volume")
    private BigDecimal volume;

    @JsonProperty("position_size")
    private BigDecimal positionSize;

    @JsonProperty("message")
    private String message;

    @JsonProperty("timestamp")
    private Long timestamp;

    // Custom fields for risk management
    @JsonProperty("maxRisk")
    private BigDecimal maxRisk;

    @JsonProperty("riskPercentage")
    private BigDecimal riskPercentage;

    /**
     * Validate the webhook request
     */
    public boolean isValid() {
        return clientId != null && !clientId.isEmpty()
                && symbol != null && !symbol.isEmpty()
                && action != null && !action.isEmpty()
                && orderQty != null && orderQty.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Convert action to uppercase for consistency
     */
    public String getNormalizedAction() {
        return action != null ? action.toUpperCase() : null;
    }

    /**
     * Get order type with default
     */
    public String getOrderTypeOrDefault() {
        return orderType != null ? orderType : "MARKET";
    }
}