package com.interview.challenge.shared.events;

import com.interview.challenge.shared.model.RiskLimit;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published when a risk violation occurs
 */
public class RiskViolationEvent implements Serializable {

    private String clientId;
    private RiskLimit.RiskType riskType;
    private BigDecimal currentValue;
    private BigDecimal threshold;
    private String reason;
    private LocalDateTime timestamp;

    public RiskViolationEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public RiskViolationEvent(String clientId, RiskLimit.RiskType riskType,
                             BigDecimal currentValue, BigDecimal threshold, String reason) {
        this.clientId = clientId;
        this.riskType = riskType;
        this.currentValue = currentValue;
        this.threshold = threshold;
        this.reason = reason;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public RiskLimit.RiskType getRiskType() {
        return riskType;
    }

    public void setRiskType(RiskLimit.RiskType riskType) {
        this.riskType = riskType;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public void setThreshold(BigDecimal threshold) {
        this.threshold = threshold;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}