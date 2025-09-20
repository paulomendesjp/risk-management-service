package com.interview.challenge.kraken.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.interview.challenge.kraken.model.KrakenAccountMonitoring.RiskLimit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Kraken User Registration Request DTO
 *
 * Represents a request to register a new Kraken user with API credentials and risk limits
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KrakenUserRegistrationRequest {

    @NotBlank(message = "Client ID is required")
    private String clientId;

    @NotBlank(message = "API Key is required")
    private String apiKey;

    @NotBlank(message = "API Secret is required")
    private String apiSecret;

    @NotNull(message = "Initial balance is required")
    @Positive(message = "Initial balance must be positive")
    private BigDecimal initialBalance;

    // Daily risk limit
    @NotNull(message = "Daily risk limit is required")
    private RiskLimit dailyRisk;

    // Maximum risk limit  
    @NotNull(message = "Maximum risk limit is required")
    private RiskLimit maxRisk;

    // Optional fields
    @Builder.Default
    private String exchange = "KRAKEN";
    
    @Builder.Default
    private String timezone = "UTC";
    
    @Builder.Default
    private Boolean active = true;

    /**
     * Create daily risk limit from percentage
     */
    @JsonProperty("dailyRiskPercent")
    public void setDailyRiskPercent(String dailyRiskPercent) {
        if (dailyRiskPercent != null) {
            this.dailyRisk = RiskLimit.builder()
                    .type("PERCENTAGE")
                    .value(new BigDecimal(dailyRiskPercent))
                    .build();
        }
    }

    /**
     * Create daily risk limit from absolute value
     */
    @JsonProperty("dailyRiskAmount")
    public void setDailyRiskAmount(String dailyRiskAmount) {
        if (dailyRiskAmount != null) {
            this.dailyRisk = RiskLimit.builder()
                    .type("ABSOLUTE")
                    .value(new BigDecimal(dailyRiskAmount))
                    .build();
        }
    }

    /**
     * Create max risk limit from percentage
     */
    @JsonProperty("maxRiskPercent")
    public void setMaxRiskPercent(String maxRiskPercent) {
        if (maxRiskPercent != null) {
            this.maxRisk = RiskLimit.builder()
                    .type("PERCENTAGE")
                    .value(new BigDecimal(maxRiskPercent))
                    .build();
        }
    }

    /**
     * Create max risk limit from absolute value
     */
    @JsonProperty("maxRiskAmount")
    public void setMaxRiskAmount(String maxRiskAmount) {
        if (maxRiskAmount != null) {
            this.maxRisk = RiskLimit.builder()
                    .type("ABSOLUTE")
                    .value(new BigDecimal(maxRiskAmount))
                    .build();
        }
    }

    /**
     * Validate the registration request
     */
    public boolean isValid() {
        if (clientId == null || clientId.trim().isEmpty()) return false;
        if (apiKey == null || apiKey.trim().isEmpty()) return false;
        if (apiSecret == null || apiSecret.trim().isEmpty()) return false;
        if (initialBalance == null || initialBalance.compareTo(BigDecimal.ZERO) <= 0) return false;
        if (dailyRisk == null || dailyRisk.getValue() == null) return false;
        if (maxRisk == null || maxRisk.getValue() == null) return false;
        return true;
    }
}
