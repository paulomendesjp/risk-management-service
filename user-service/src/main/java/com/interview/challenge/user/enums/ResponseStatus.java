package com.interview.challenge.user.enums;

public enum ResponseStatus {
    SUCCESS("success"),
    ERROR("error"),
    WARNING("warning");

    private final String value;

    ResponseStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}