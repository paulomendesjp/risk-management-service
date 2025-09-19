package com.interview.challenge.kraken.service;

import com.interview.challenge.kraken.model.KrakenAccountMonitoring;
import com.interview.challenge.kraken.model.KrakenAccountMonitoring.RiskLimit;
import com.interview.challenge.kraken.model.KrakenAccountMonitoring.RiskViolation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Risk calculation logic for Kraken accounts
 * Handles daily risk and max risk calculations
 */
@Slf4j
@Component
public class KrakenRiskCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal WARNING_THRESHOLD = new BigDecimal("0.8"); // 80% warning

    /**
     * Calculate if account has risk violation
     */
    public RiskCheckResult checkRisk(KrakenAccountMonitoring account) {
        RiskCheckResult result = new RiskCheckResult();
        result.setClientId(account.getClientId());

        // Check max risk first (more severe)
        if (account.getMaxRisk() != null) {
            checkMaxRisk(account, result);
        }

        // Check daily risk
        if (account.getDailyRisk() != null && !result.isMaxRiskViolated()) {
            checkDailyRisk(account, result);
        }

        // Set overall status
        if (result.isMaxRiskViolated()) {
            result.setStatus("MAX_RISK_VIOLATED");
        } else if (result.isDailyRiskViolated()) {
            result.setStatus("DAILY_RISK_VIOLATED");
        } else if (result.isWarning()) {
            result.setStatus("WARNING");
        } else {
            result.setStatus("NORMAL");
        }

        log.debug("Risk check for {}: {}", account.getClientId(), result.getStatus());

        return result;
    }

    /**
     * Check max risk violation
     */
    private void checkMaxRisk(KrakenAccountMonitoring account, RiskCheckResult result) {
        BigDecimal threshold = calculateThreshold(
            account.getMaxRisk(),
            account.getInitialBalance()
        );

        if (account.getTotalPnl() != null && account.getTotalPnl().compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal loss = account.getTotalPnl().negate();
            BigDecimal percentage = calculatePercentage(loss, threshold);

            result.setMaxRiskLoss(loss);
            result.setMaxRiskThreshold(threshold);
            result.setMaxRiskPercentage(percentage);

            if (loss.compareTo(threshold) >= 0) {
                result.setMaxRiskViolated(true);
                log.error("🚨 MAX RISK VIOLATED for {}: Loss ${} >= Threshold ${}",
                    account.getClientId(), loss, threshold);
            } else if (percentage.compareTo(new BigDecimal("80")) >= 0) {
                result.setWarning(true);
                log.warn("⚠️ MAX RISK WARNING for {}: {}% of limit reached",
                    account.getClientId(), percentage);
            }
        }
    }

    /**
     * Check daily risk violation
     */
    private void checkDailyRisk(KrakenAccountMonitoring account, RiskCheckResult result) {
        BigDecimal threshold = calculateThreshold(
            account.getDailyRisk(),
            account.getDailyStartBalance() != null ? account.getDailyStartBalance() : account.getInitialBalance()
        );

        if (account.getDailyPnl() != null && account.getDailyPnl().compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal loss = account.getDailyPnl().negate();
            BigDecimal percentage = calculatePercentage(loss, threshold);

            result.setDailyRiskLoss(loss);
            result.setDailyRiskThreshold(threshold);
            result.setDailyRiskPercentage(percentage);

            if (loss.compareTo(threshold) >= 0) {
                result.setDailyRiskViolated(true);
                log.warn("⚠️ DAILY RISK VIOLATED for {}: Loss ${} >= Threshold ${}",
                    account.getClientId(), loss, threshold);
            } else if (percentage.compareTo(new BigDecimal("80")) >= 0) {
                result.setWarning(true);
                log.info("📊 DAILY RISK WARNING for {}: {}% of limit reached",
                    account.getClientId(), percentage);
            }
        }
    }

    /**
     * Calculate risk threshold based on type (percentage or absolute)
     */
    public BigDecimal calculateThreshold(RiskLimit limit, BigDecimal baseAmount) {
        if (limit == null) {
            return BigDecimal.ZERO;
        }

        if (limit.isPercentage() && baseAmount != null) {
            // Calculate percentage of base amount
            return baseAmount
                .multiply(limit.getValue())
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        } else {
            // Use absolute value
            return limit.getValue();
        }
    }

    /**
     * Calculate percentage of threshold reached
     */
    private BigDecimal calculatePercentage(BigDecimal actual, BigDecimal threshold) {
        if (threshold == null || threshold.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return actual
            .multiply(HUNDRED)
            .divide(threshold, 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate daily P&L
     */
    public BigDecimal calculateDailyPnL(BigDecimal currentBalance, BigDecimal dailyStartBalance) {
        if (currentBalance == null || dailyStartBalance == null) {
            return BigDecimal.ZERO;
        }
        return currentBalance.subtract(dailyStartBalance);
    }

    /**
     * Calculate total P&L
     */
    public BigDecimal calculateTotalPnL(BigDecimal currentBalance, BigDecimal initialBalance) {
        if (currentBalance == null || initialBalance == null) {
            return BigDecimal.ZERO;
        }
        return currentBalance.subtract(initialBalance);
    }

    /**
     * Risk check result
     */
    public static class RiskCheckResult {
        private String clientId;
        private String status;
        private boolean dailyRiskViolated;
        private boolean maxRiskViolated;
        private boolean warning;

        private BigDecimal dailyRiskLoss;
        private BigDecimal dailyRiskThreshold;
        private BigDecimal dailyRiskPercentage;

        private BigDecimal maxRiskLoss;
        private BigDecimal maxRiskThreshold;
        private BigDecimal maxRiskPercentage;

        // Getters and setters
        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public boolean isDailyRiskViolated() {
            return dailyRiskViolated;
        }

        public void setDailyRiskViolated(boolean dailyRiskViolated) {
            this.dailyRiskViolated = dailyRiskViolated;
        }

        public boolean isMaxRiskViolated() {
            return maxRiskViolated;
        }

        public void setMaxRiskViolated(boolean maxRiskViolated) {
            this.maxRiskViolated = maxRiskViolated;
        }

        public boolean isWarning() {
            return warning;
        }

        public void setWarning(boolean warning) {
            this.warning = warning;
        }

        public BigDecimal getDailyRiskLoss() {
            return dailyRiskLoss;
        }

        public void setDailyRiskLoss(BigDecimal dailyRiskLoss) {
            this.dailyRiskLoss = dailyRiskLoss;
        }

        public BigDecimal getDailyRiskThreshold() {
            return dailyRiskThreshold;
        }

        public void setDailyRiskThreshold(BigDecimal dailyRiskThreshold) {
            this.dailyRiskThreshold = dailyRiskThreshold;
        }

        public BigDecimal getDailyRiskPercentage() {
            return dailyRiskPercentage;
        }

        public void setDailyRiskPercentage(BigDecimal dailyRiskPercentage) {
            this.dailyRiskPercentage = dailyRiskPercentage;
        }

        public BigDecimal getMaxRiskLoss() {
            return maxRiskLoss;
        }

        public void setMaxRiskLoss(BigDecimal maxRiskLoss) {
            this.maxRiskLoss = maxRiskLoss;
        }

        public BigDecimal getMaxRiskThreshold() {
            return maxRiskThreshold;
        }

        public void setMaxRiskThreshold(BigDecimal maxRiskThreshold) {
            this.maxRiskThreshold = maxRiskThreshold;
        }

        public BigDecimal getMaxRiskPercentage() {
            return maxRiskPercentage;
        }

        public void setMaxRiskPercentage(BigDecimal maxRiskPercentage) {
            this.maxRiskPercentage = maxRiskPercentage;
        }

        public boolean hasViolation() {
            return dailyRiskViolated || maxRiskViolated;
        }

        @Override
        public String toString() {
            return String.format("RiskCheckResult{clientId='%s', status='%s', dailyRisk=%s, maxRisk=%s}",
                clientId, status, dailyRiskViolated, maxRiskViolated);
        }
    }
}