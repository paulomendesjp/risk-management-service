package com.interview.challenge.shared.enums;

/**
 * Enum representing all possible notification event types in the system
 */
public enum NotificationEventType {

    DAILY_RISK_TRIGGERED("Daily risk limit exceeded"),
    MAX_RISK_TRIGGERED("Maximum risk limit exceeded"),
    BALANCE_UPDATE("Account balance changed"),
    MONITORING_ERROR("Error in monitoring system"),
    POSITION_CLOSED("Position was closed"),
    ACCOUNT_BLOCKED("Account was blocked"),
    SYSTEM_EVENT("General system event");

    private final String description;

    NotificationEventType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}