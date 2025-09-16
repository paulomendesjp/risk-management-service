package com.interview.challenge.notification.enums;

/**
 * Enum representing notification priority levels
 */
public enum NotificationPriority {

    CRITICAL(1, "Critical priority - Immediate action required"),
    HIGH(2, "High priority - Urgent attention needed"),
    NORMAL(3, "Normal priority - Standard notification"),
    LOW(4, "Low priority - Informational");

    private final int level;
    private final String description;

    NotificationPriority(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }

    public boolean isHigherThan(NotificationPriority other) {
        return this.level < other.level;
    }

    public static NotificationPriority fromString(String text) {
        for (NotificationPriority priority : NotificationPriority.values()) {
            if (priority.name().equalsIgnoreCase(text)) {
                return priority;
            }
        }
        return NORMAL;
    }
}