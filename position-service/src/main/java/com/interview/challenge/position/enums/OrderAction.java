package com.interview.challenge.position.enums;

/**
 * Enum representing order actions/sides
 */
public enum OrderAction {
    BUY("Buy", "Purchase order"),
    SELL("Sell", "Sell order"),
    SHORT("Short", "Short sell order"),
    COVER("Cover", "Cover short position");

    private final String displayName;
    private final String description;

    OrderAction(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isOpeningPosition() {
        return this == BUY || this == SHORT;
    }

    public boolean isClosingPosition() {
        return this == SELL || this == COVER;
    }
}