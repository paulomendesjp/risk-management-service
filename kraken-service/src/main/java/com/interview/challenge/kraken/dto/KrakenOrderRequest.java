package com.interview.challenge.kraken.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Kraken Order Request DTO
 *
 * Represents an order to be placed on Kraken Futures
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KrakenOrderRequest {

    @NotBlank(message = "Client ID is required")
    private String clientId;

    @NotBlank(message = "Symbol is required")
    private String symbol; // e.g., "PI_ETHUSD", "PI_XBTUSD"

    @NotBlank(message = "Side is required")
    @JsonProperty("side")
    private String side; // "buy" or "sell"
    
    // Support legacy "action" field for backward compatibility
    @JsonProperty("action")
    public void setAction(String action) {
        this.side = action;
    }

    @NotNull(message = "Order quantity is required")
    @Positive(message = "Order quantity must be positive")
    private BigDecimal orderQty;

    @Builder.Default
    private String orderType = "mkt"; // "mkt" for market, "lmt" for limit

    private BigDecimal limitPrice; // Required for limit orders

    private BigDecimal stopPrice; // For stop orders

    @JsonProperty("stopLoss")
    private BigDecimal stopLossPercentage; // Stop loss in percentage
    
    // Support legacy field names with % suffix
    @JsonProperty("stopLoss%")
    public void setStopLossPercent(String stopLossPercent) {
        if (stopLossPercent != null) {
            this.stopLossPercentage = new BigDecimal(stopLossPercent);
        }
    }

    @JsonProperty("takeProfit")
    private BigDecimal takeProfitPercentage; // Take profit in percentage

    private String strategy; // Strategy identifier

    @Builder.Default
    private Boolean reduceOnly = false; // Only reduce position

    @Builder.Default
    private Boolean postOnly = false; // Post-only order (maker only)

    // Risk management fields
    @JsonProperty("maxriskperday")
    private BigDecimal maxRiskPerDay; // Max risk per day in percentage
    
    // Support legacy field names with % suffix
    @JsonProperty("maxriskperday%")
    public void setMaxRiskPerDayPercent(String maxRiskPerDayPercent) {
        if (maxRiskPerDayPercent != null) {
            this.maxRiskPerDay = new BigDecimal(maxRiskPerDayPercent);
        }
    }

    @Builder.Default
    private Boolean inverse = false; // Close and reverse position

    @Builder.Default
    private Boolean pyramid = false; // Allow multiple same-direction orders

    // Kraken specific fields
    private String cliOrdId; // Client order ID for tracking

    private Integer maxShow; // Iceberg order quantity

    // Webhook source tracking
    private String source; // "tradingview", "manual", etc.

    /**
     * Convert to Kraken API format
     */
    public String toKrakenFormat() {
        StringBuilder params = new StringBuilder();
        params.append("orderType=").append(orderType);
        params.append("&symbol=").append(symbol);
        params.append("&side=").append(side);
        params.append("&size=").append(orderQty);

        if ("lmt".equals(orderType) && limitPrice != null) {
            params.append("&limitPrice=").append(limitPrice);
        }

        if (stopPrice != null) {
            params.append("&stopPrice=").append(stopPrice);
        }

        if (reduceOnly) {
            params.append("&reduceOnly=true");
        }

        if (postOnly) {
            params.append("&postOnly=true");
        }

        if (cliOrdId != null) {
            params.append("&cliOrdId=").append(cliOrdId);
        }

        return params.toString();
    }

    /**
     * Validate the order request
     */
    public boolean isValid() {
        if (symbol == null || symbol.isEmpty()) return false;
        if (side == null || (!side.equalsIgnoreCase("buy") && !side.equalsIgnoreCase("sell"))) return false;
        if (orderQty == null || orderQty.compareTo(BigDecimal.ZERO) <= 0) return false;
        if ("lmt".equals(orderType) && (limitPrice == null || limitPrice.compareTo(BigDecimal.ZERO) <= 0)) return false;
        return true;
    }

    /**
     * Get normalized side (uppercase)
     */
    public String getNormalizedSide() {
        return side != null ? side.toLowerCase() : null;
    }

    /**
     * Get Kraken symbol format
     * Converts from standard format (ETH/USD) to Kraken format (PI_ETHUSD)
     */
    public String getKrakenSymbol() {
        if (symbol == null) return null;

        // If already in Kraken format
        if (symbol.startsWith("PI_") || symbol.startsWith("FI_")) {
            return symbol;
        }

        // Convert ETH/USD to PI_ETHUSD, BTC/USD to PI_XBTUSD
        String normalized = symbol.replace("/", "").toUpperCase();
        if (normalized.equals("BTCUSD")) {
            normalized = "XBTUSD";
        }

        return "PI_" + normalized;
    }
}