package com.interview.challenge.position.enums;

/**
 * Enum representing order status states
 */
public enum OrderStatus {
    PENDING("Pending", "Order is pending execution"),
    FILLED("Filled", "Order has been filled"),
    PARTIALLY_FILLED("Partially Filled", "Order has been partially filled"),
    CANCELLED("Cancelled", "Order has been cancelled"),
    REJECTED("Rejected", "Order has been rejected"),
    EXPIRED("Expired", "Order has expired"),
    FAILED("Failed", "Order execution failed");

    private final String displayName;
    private final String description;

    OrderStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == FILLED || this == CANCELLED ||
               this == REJECTED || this == EXPIRED || this == FAILED;
    }

    public boolean isActive() {
        return this == PENDING || this == PARTIALLY_FILLED;
    }
}