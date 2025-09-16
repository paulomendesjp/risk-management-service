package com.interview.challenge.shared.event;

import com.interview.challenge.shared.model.RiskType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Risk Violation Event
 *
 * Published when a risk limit is violated
 */
public class RiskViolationEvent {
    private String clientId;
    private RiskType riskType;
    private BigDecimal threshold;
    private BigDecimal currentValue;
    private String action;
    private LocalDateTime timestamp;

    public RiskViolationEvent() {
    }

    public RiskViolationEvent(String clientId, RiskType riskType, BigDecimal threshold,
                             BigDecimal currentValue, String action, LocalDateTime timestamp) {
        this.clientId = clientId;
        this.riskType = riskType;
        this.threshold = threshold;
        this.currentValue = currentValue;
        this.action = action;
        this.timestamp = timestamp;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public RiskType getRiskType() {
        return riskType;
    }

    public void setRiskType(RiskType riskType) {
        this.riskType = riskType;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public void setThreshold(BigDecimal threshold) {
        this.threshold = threshold;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}