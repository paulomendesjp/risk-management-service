package com.interview.challenge.risk.model;

/**
 * Risk Status Enumeration
 *
 * Represents the current risk status of an account
 */
public enum RiskStatus {
    HEALTHY,              // Account is within all risk limits
    NORMAL,               // Normal operating status
    WARNING,              // Approaching risk limits (80% threshold)
    CRITICAL,             // Risk limits have been breached
    UNKNOWN,              // Unable to determine risk status
    MAX_RISK_TRIGGERED,   // Maximum risk limit has been triggered
    DAILY_RISK_TRIGGERED, // Daily risk limit has been triggered
    MONITORING_ERROR      // Error occurred during monitoring
}