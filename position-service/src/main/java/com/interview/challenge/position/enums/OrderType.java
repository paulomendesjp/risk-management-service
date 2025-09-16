package com.interview.challenge.position.enums;

/**
 * Enum representing order types
 */
public enum OrderType {
    MARKET("Market", "Market order - executed immediately at current market price"),
    LIMIT("Limit", "Limit order - executed at specified price or better"),
    STOP("Stop", "Stop order - becomes market order when stop price is reached"),
    STOP_LIMIT("Stop Limit", "Stop limit order - becomes limit order when stop price is reached"),
    TRAILING_STOP("Trailing Stop", "Stop order that adjusts with market price"),
    STOP_LOSS("Stop Loss", "Stop loss order for risk management");

    private final String displayName;
    private final String description;

    OrderType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isImmediate() {
        return this == MARKET;
    }

    public boolean isConditional() {
        return this == STOP || this == STOP_LIMIT ||
               this == TRAILING_STOP || this == STOP_LOSS;
    }
}