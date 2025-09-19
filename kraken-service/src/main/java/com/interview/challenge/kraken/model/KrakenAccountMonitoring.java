package com.interview.challenge.kraken.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity to store Kraken account monitoring data
 * Tracks balance, P&L, risk status, and blocking for each client
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "kraken_account_monitoring")
public class KrakenAccountMonitoring {

    @Id
    private String id;

    @Indexed(unique = true)
    private String clientId;

    private String apiKey;
    private String apiSecret;

    // Balance tracking
    private BigDecimal initialBalance;
    private BigDecimal currentBalance;
    private BigDecimal dailyStartBalance;
    private BigDecimal previousBalance;

    // P&L tracking
    private BigDecimal totalPnl;
    private BigDecimal dailyPnl;
    private BigDecimal realizedPnl;
    private BigDecimal unrealizedPnl;

    // Risk limits
    private RiskLimit dailyRisk;
    private RiskLimit maxRisk;

    // Risk status
    private boolean dailyBlocked;
    private boolean permanentBlocked;
    private LocalDateTime dailyBlockedAt;
    private LocalDateTime permanentBlockedAt;
    private String dailyBlockReason;
    private String permanentBlockReason;

    // Monitoring status
    private boolean active;
    private LocalDateTime lastChecked;
    private LocalDateTime lastBalanceUpdate;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime dailyResetAt;

    // Trading status
    private int openPositions;
    private int dailyTrades;
    private BigDecimal dailyVolume;

    /**
     * Risk limit configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskLimit {
        private String type; // "percentage" or "absolute"
        private BigDecimal value;

        public boolean isPercentage() {
            return "percentage".equalsIgnoreCase(type);
        }

        public boolean isAbsolute() {
            return "absolute".equalsIgnoreCase(type);
        }
    }

    /**
     * Check if account can trade
     */
    public boolean canTrade() {
        return active && !dailyBlocked && !permanentBlocked;
    }

    /**
     * Block account for daily risk violation
     */
    public void blockDaily(String reason) {
        this.dailyBlocked = true;
        this.dailyBlockedAt = LocalDateTime.now();
        this.dailyBlockReason = reason;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Block account permanently for max risk violation
     */
    public void blockPermanently(String reason) {
        this.permanentBlocked = true;
        this.permanentBlockedAt = LocalDateTime.now();
        this.permanentBlockReason = reason;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Reset daily tracking (called at 00:01 UTC)
     */
    public void resetDaily() {
        this.dailyBlocked = false;
        this.dailyBlockedAt = null;
        this.dailyBlockReason = null;
        this.dailyStartBalance = this.currentBalance;
        this.dailyPnl = BigDecimal.ZERO;
        this.dailyTrades = 0;
        this.dailyVolume = BigDecimal.ZERO;
        this.dailyResetAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Update balance and calculate P&L
     */
    public void updateBalance(BigDecimal newBalance) {
        this.previousBalance = this.currentBalance;
        this.currentBalance = newBalance;

        // Calculate P&L
        if (this.initialBalance != null) {
            this.totalPnl = newBalance.subtract(this.initialBalance);
        }

        if (this.dailyStartBalance != null) {
            this.dailyPnl = newBalance.subtract(this.dailyStartBalance);
        }

        this.lastBalanceUpdate = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if daily risk is violated
     */
    public boolean isDailyRiskViolated() {
        if (dailyRisk == null || dailyPnl == null) {
            return false;
        }

        BigDecimal threshold = calculateRiskThreshold(dailyRisk, dailyStartBalance);
        BigDecimal loss = dailyPnl.negate(); // Convert to positive loss

        return loss.compareTo(threshold) > 0;
    }

    /**
     * Check if max risk is violated
     */
    public boolean isMaxRiskViolated() {
        if (maxRisk == null || totalPnl == null) {
            return false;
        }

        BigDecimal threshold = calculateRiskThreshold(maxRisk, initialBalance);
        BigDecimal loss = totalPnl.negate(); // Convert to positive loss

        return loss.compareTo(threshold) > 0;
    }

    /**
     * Calculate risk threshold based on type
     */
    private BigDecimal calculateRiskThreshold(RiskLimit limit, BigDecimal baseAmount) {
        if (limit.isPercentage() && baseAmount != null) {
            return baseAmount.multiply(limit.getValue()).divide(new BigDecimal(100));
        } else {
            return limit.getValue();
        }
    }

    /**
     * Get risk violation details
     */
    public RiskViolation getRiskViolation() {
        if (isMaxRiskViolated()) {
            BigDecimal threshold = calculateRiskThreshold(maxRisk, initialBalance);
            return new RiskViolation("MAX_RISK", totalPnl.negate(), threshold);
        }

        if (isDailyRiskViolated()) {
            BigDecimal threshold = calculateRiskThreshold(dailyRisk, dailyStartBalance);
            return new RiskViolation("DAILY_RISK", dailyPnl.negate(), threshold);
        }

        return null;
    }

    /**
     * Risk violation details
     */
    @Data
    @AllArgsConstructor
    public static class RiskViolation {
        private String type;
        private BigDecimal loss;
        private BigDecimal threshold;
    }
}