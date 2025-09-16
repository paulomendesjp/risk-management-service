package com.interview.challenge.shared.model;

import java.math.BigDecimal;

/**
 * Risk Limit Model
 *
 * Represents risk limits for trading accounts as per requirements:
 * - Max Risk: Maximum loss threshold (absolute value or % of initial balance)
 * - Daily Risk: Daily loss threshold (absolute value or % of initial balance)
 */
public class RiskLimit {

    /**
     * Risk type enum for clear type safety
     */
    public enum RiskType {
        DAILY_RISK("Daily Risk Limit"),
        MAX_RISK("Maximum Risk Limit");

        private final String description;

        RiskType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private String type; // "percentage" or "absolute"
    private BigDecimal value;
    private RiskType riskType;

    public RiskLimit() {}

    public RiskLimit(String type, BigDecimal value) {
        this.type = type;
        this.value = value;
    }

    public RiskLimit(RiskType riskType, String type, BigDecimal value) {
        this.riskType = riskType;
        this.type = type;
        this.value = value;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public BigDecimal getValue() {
        return value;
    }
    
    public void setValue(BigDecimal value) {
        this.value = value;
    }
    
    public RiskType getRiskType() {
        return riskType;
    }

    public void setRiskType(RiskType riskType) {
        this.riskType = riskType;
    }

    /**
     * Calculate absolute limit based on initial balance
     */
    public BigDecimal calculateAbsoluteLimit(BigDecimal initialBalance) {
        if ("percentage".equals(type)) {
            return initialBalance.multiply(value).divide(new BigDecimal("100"));
        } else {
            return value;
        }
    }

    /**
     * Check if this is a percentage-based limit
     */
    public boolean isPercentage() {
        return "percentage".equalsIgnoreCase(type);
    }

    /**
     * Check if this is an absolute value limit
     */
    public boolean isAbsolute() {
        return "absolute".equalsIgnoreCase(type);
    }

    @Override
    public String toString() {
        return String.format("RiskLimit{type='%s', value=%s, riskType=%s}",
                type, value, riskType);
    }
}
