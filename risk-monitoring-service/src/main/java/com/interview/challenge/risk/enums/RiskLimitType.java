package com.interview.challenge.risk.enums;

public enum RiskLimitType {
    PERCENTAGE("percentage"),
    ABSOLUTE("absolute");

    private final String value;

    RiskLimitType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static RiskLimitType fromValue(String value) {
        for (RiskLimitType type : RiskLimitType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown RiskLimitType: " + value);
    }
}