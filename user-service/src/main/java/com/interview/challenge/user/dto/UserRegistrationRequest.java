package com.interview.challenge.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * User Registration Request DTO
 * 
 * Follows EXACT requirements from document:
 * - clientId: unique identifier
 * - apiKey and apiSecret: Architect account credentials
 * - maxRisk: Maximum loss threshold (absolute value or % of initial balance)
 * - dailyRisk: Daily loss threshold (absolute value or % of initial balance)
 */
public class UserRegistrationRequest {

    @NotBlank(message = "Client ID is required")
    private String clientId;

    @NotBlank(message = "API Key is required")
    private String apiKey;

    @NotBlank(message = "API Secret is required")
    private String apiSecret;

    @Valid
    @NotNull(message = "Max Risk configuration is required")
    private RiskLimitDto maxRisk;

    @Valid
    @NotNull(message = "Daily Risk configuration is required")
    private RiskLimitDto dailyRisk;

    @PositiveOrZero(message = "Initial balance must be positive or zero")
    private BigDecimal initialBalance;  // Optional: if not provided, fetches from exchange

    private String exchange = "ARCHITECT";  // ARCHITECT or KRAKEN (default: ARCHITECT for backward compatibility)

    // Constructors
    public UserRegistrationRequest() {}

    public UserRegistrationRequest(String clientId, String apiKey, String apiSecret, 
                                 RiskLimitDto maxRisk, RiskLimitDto dailyRisk) {
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.maxRisk = maxRisk;
        this.dailyRisk = dailyRisk;
    }

    // Getters and Setters
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public RiskLimitDto getMaxRisk() {
        return maxRisk;
    }

    public void setMaxRisk(RiskLimitDto maxRisk) {
        this.maxRisk = maxRisk;
    }

    public RiskLimitDto getDailyRisk() {
        return dailyRisk;
    }

    public void setDailyRisk(RiskLimitDto dailyRisk) {
        this.dailyRisk = dailyRisk;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    /**
     * Risk Limit DTO
     * 
     * type: "percentage" or "absolute"
     * value: numeric value of the limit
     */
    public static class RiskLimitDto {
        
        @NotBlank(message = "Risk limit type is required (percentage or absolute)")
        private String type;

        @Positive(message = "Risk limit value must be positive")
        private double value;

        // Constructors
        public RiskLimitDto() {}

        public RiskLimitDto(String type, double value) {
            this.type = type;
            this.value = value;
        }

        // Getters and Setters
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public double getValue() {
            return value;
        }

        public void setValue(double value) {
            this.value = value;
        }

        public boolean isValid() {
            return type != null &&
                   ("percentage".equalsIgnoreCase(type) || "absolute".equalsIgnoreCase(type)) &&
                   value > 0;
        }

        @Override
        public String toString() {
            return String.format("RiskLimit{type='%s', value=%.2f}", type, value);
        }
    }

    @Override
    public String toString() {
        return String.format("UserRegistrationRequest{clientId='%s', maxRisk=%s, dailyRisk=%s}", 
                           clientId, maxRisk, dailyRisk);
    }
}



