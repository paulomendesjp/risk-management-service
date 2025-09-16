package com.interview.challenge.notification.enums;

/**
 * Enum representing all possible notification event types in the system
 */
public enum NotificationEventType {

    DAILY_RISK_TRIGGERED("Daily risk limit exceeded", "HIGH"),
    MAX_RISK_TRIGGERED("Maximum risk limit exceeded", "CRITICAL"),
    BALANCE_UPDATE("Account balance changed", "LOW"),
    MONITORING_ERROR("Error in monitoring system", "HIGH"),
    POSITION_CLOSED("Position was closed", "NORMAL"),
    ACCOUNT_BLOCKED("Account was blocked", "CRITICAL"),
    SYSTEM_EVENT("General system event", "NORMAL");

    private final String description;
    private final String defaultPriority;

    NotificationEventType(String description, String defaultPriority) {
        this.description = description;
        this.defaultPriority = defaultPriority;
    }

    public String getDescription() {
        return description;
    }

    public String getDefaultPriority() {
        return defaultPriority;
    }

    public static NotificationEventType fromString(String text) {
        for (NotificationEventType type : NotificationEventType.values()) {
            if (type.name().equalsIgnoreCase(text)) {
                return type;
            }
        }
        return SYSTEM_EVENT;
    }
}