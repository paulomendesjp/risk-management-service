package com.interview.challenge.risk.enums;

public enum ViolationType {
    MAX_RISK("MAX_RISK", "Max Risk Violation", true),
    DAILY_RISK("DAILY_RISK", "Daily Risk Violation", false);

    private final String code;
    private final String description;
    private final boolean isPermanent;

    ViolationType(String code, String description, boolean isPermanent) {
        this.code = code;
        this.description = description;
        this.isPermanent = isPermanent;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPermanent() {
        return isPermanent;
    }

    public static ViolationType fromCode(String code) {
        for (ViolationType type : ViolationType.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ViolationType code: " + code);
    }
}