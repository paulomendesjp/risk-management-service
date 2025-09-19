package com.interview.challenge.shared.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Client Configuration Entity
 * Stores user configuration and risk management settings
 *
 * Follows EXACT requirements from document:
 * - api_key and api_secret of their Architect account
 * - Initial Balance (fetched at the start)
 * - Risk Limits: Max Risk and Daily Risk
 */
@Document(collection = "client_configurations")
public class ClientConfiguration {

    @Id
    private String id;

    @NotBlank(message = "Client ID is required")
    private String clientId;

    @NotBlank(message = "API key is required")
    private String apiKey; // Encrypted

    @NotBlank(message = "API secret is required")
    private String apiSecret; // Encrypted

    @NotNull(message = "Initial balance is required")
    private BigDecimal initialBalance;

    @NotNull(message = "Max risk configuration is required")
    private RiskLimit maxRisk;

    @NotNull(message = "Daily risk configuration is required")
    private RiskLimit dailyRisk;

    private String exchange = "ARCHITECT"; // ARCHITECT or KRAKEN

    // Blocking status
    private boolean dailyBlocked = false;
    private boolean permanentBlocked = false;

    // Blocking reasons
    private String dailyBlockReason;
    private LocalDateTime dailyBlockedAt;
    private String permanentBlockReason;
    private LocalDateTime permanentBlockedAt;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastBlockedAt;

    // Constructors
    public ClientConfiguration() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public ClientConfiguration(String clientId, String apiKey, String apiSecret,
                             BigDecimal initialBalance, RiskLimit maxRisk, RiskLimit dailyRisk) {
        this();
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.initialBalance = initialBalance;
        this.maxRisk = maxRisk;
        this.dailyRisk = dailyRisk;
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

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }

    public RiskLimit getMaxRisk() {
        return maxRisk;
    }

    public void setMaxRisk(RiskLimit maxRisk) {
        this.maxRisk = maxRisk;
    }

    public RiskLimit getDailyRisk() {
        return dailyRisk;
    }

    public void setDailyRisk(RiskLimit dailyRisk) {
        this.dailyRisk = dailyRisk;
    }

    public boolean isDailyBlocked() {
        return dailyBlocked;
    }

    public void setDailyBlocked(boolean dailyBlocked) {
        this.dailyBlocked = dailyBlocked;
        this.updatedAt = LocalDateTime.now();
        if (dailyBlocked) {
            this.lastBlockedAt = LocalDateTime.now();
        }
    }

    public boolean isPermanentBlocked() {
        return permanentBlocked;
    }

    public void setPermanentBlocked(boolean permanentBlocked) {
        this.permanentBlocked = permanentBlocked;
        this.updatedAt = LocalDateTime.now();
        if (permanentBlocked) {
            this.lastBlockedAt = LocalDateTime.now();
        }
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getLastBlockedAt() {
        return lastBlockedAt;
    }

    public void setLastBlockedAt(LocalDateTime lastBlockedAt) {
        this.lastBlockedAt = lastBlockedAt;
    }

    // Utility methods
    public boolean isBlocked() {
        return dailyBlocked || permanentBlocked;
    }

    public boolean canTrade() {
        return !isBlocked();
    }

    public void blockDaily() {
        setDailyBlocked(true);
    }

    public void blockPermanently() {
        setPermanentBlocked(true);
    }

    public void resetDailyBlock() {
        setDailyBlocked(false);
    }

    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }

    // Additional getters and setters for blocking reasons
    public String getDailyBlockReason() {
        return dailyBlockReason;
    }

    public void setDailyBlockReason(String dailyBlockReason) {
        this.dailyBlockReason = dailyBlockReason;
    }

    public LocalDateTime getDailyBlockedAt() {
        return dailyBlockedAt;
    }

    public void setDailyBlockedAt(LocalDateTime dailyBlockedAt) {
        this.dailyBlockedAt = dailyBlockedAt;
    }

    public String getPermanentBlockReason() {
        return permanentBlockReason;
    }

    public void setPermanentBlockReason(String permanentBlockReason) {
        this.permanentBlockReason = permanentBlockReason;
    }

    public LocalDateTime getPermanentBlockedAt() {
        return permanentBlockedAt;
    }

    public void setPermanentBlockedAt(LocalDateTime permanentBlockedAt) {
        this.permanentBlockedAt = permanentBlockedAt;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    @Override
    public String toString() {
        return "ClientConfiguration{" +
                "id='" + id + '\'' +
                ", clientId='" + clientId + '\'' +
                ", initialBalance=" + initialBalance +
                ", dailyBlocked=" + dailyBlocked +
                ", permanentBlocked=" + permanentBlocked +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}