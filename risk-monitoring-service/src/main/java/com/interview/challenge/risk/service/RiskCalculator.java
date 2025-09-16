package com.interview.challenge.risk.service;

import com.interview.challenge.risk.constants.RiskConstants;
import com.interview.challenge.risk.enums.RiskLimitType;
import com.interview.challenge.risk.model.AccountMonitoring;
import com.interview.challenge.shared.model.ClientConfiguration;
import com.interview.challenge.shared.model.RiskLimit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class RiskCalculator {

    private static final Logger logger = LoggerFactory.getLogger(RiskCalculator.class);

    /**
     * Calculate risk threshold based on limit type and base amount
     */
    public BigDecimal calculateRiskThreshold(RiskLimit riskLimit, BigDecimal baseAmount) {
        if (riskLimit == null || baseAmount == null) {
            return BigDecimal.ZERO;
        }

        RiskLimitType type = RiskLimitType.fromValue(riskLimit.getType());

        switch (type) {
            case PERCENTAGE:
                return calculatePercentageThreshold(riskLimit.getValue(), baseAmount);
            case ABSOLUTE:
                return riskLimit.getValue();
            default:
                logger.warn("Unknown risk limit type: {}, treating as absolute", riskLimit.getType());
                return riskLimit.getValue();
        }
    }

    /**
     * Calculate percentage-based threshold
     */
    private BigDecimal calculatePercentageThreshold(BigDecimal percentage, BigDecimal baseAmount) {
        return baseAmount.multiply(percentage.divide(RiskConstants.PERCENTAGE_DIVISOR));
    }

    /**
     * Check if current loss exceeds the risk limit
     */
    public boolean isRiskLimitExceeded(BigDecimal currentLoss, BigDecimal threshold) {
        return currentLoss.compareTo(threshold) >= 0;
    }

    /**
     * Check if account is near risk limit (80% threshold)
     */
    public boolean isNearRiskLimit(BigDecimal currentLoss, BigDecimal threshold) {
        BigDecimal warningThreshold = threshold.multiply(RiskConstants.WARNING_THRESHOLD_MULTIPLIER);
        return currentLoss.compareTo(warningThreshold) >= 0;
    }

    /**
     * Calculate max risk limit for an account
     */
    public BigDecimal calculateMaxRiskLimit(ClientConfiguration config, AccountMonitoring monitoring) {
        if (config == null || config.getMaxRisk() == null || monitoring == null) {
            return BigDecimal.ZERO;
        }
        return calculateRiskThreshold(config.getMaxRisk(), monitoring.getInitialBalance());
    }

    /**
     * Calculate daily risk limit for an account
     */
    public BigDecimal calculateDailyRiskLimit(ClientConfiguration config, AccountMonitoring monitoring) {
        if (config == null || config.getDailyRisk() == null || monitoring == null) {
            return BigDecimal.ZERO;
        }
        return calculateRiskThreshold(config.getDailyRisk(), monitoring.getDailyStartBalance());
    }

    /**
     * Create default max risk limit (30% of initial balance)
     */
    public RiskLimit createDefaultMaxRiskLimit() {
        return new RiskLimit(
            RiskLimitType.PERCENTAGE.getValue(),
            new BigDecimal(RiskConstants.DEFAULT_MAX_RISK_PERCENTAGE)
        );
    }

    /**
     * Create default daily risk limit ($5000 absolute)
     */
    public RiskLimit createDefaultDailyRiskLimit() {
        return new RiskLimit(
            RiskLimitType.ABSOLUTE.getValue(),
            new BigDecimal(RiskConstants.DEFAULT_DAILY_RISK_AMOUNT)
        );
    }

    /**
     * Calculate loss percentage
     */
    public BigDecimal calculateLossPercentage(BigDecimal loss, BigDecimal baseAmount) {
        if (baseAmount == null || baseAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return loss.divide(baseAmount, 4, java.math.RoundingMode.HALF_UP)
                   .multiply(RiskConstants.PERCENTAGE_DIVISOR);
    }
}