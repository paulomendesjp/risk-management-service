package com.interview.challenge.user.enums;

public enum ErrorCode {
    VALIDATION_ERROR("VALIDATION_ERROR", "Validation failed"),
    REGISTRATION_FAILED("REGISTRATION_FAILED", "Failed to register user"),
    USER_NOT_FOUND("USER_NOT_FOUND", "User not found"),
    USER_ALREADY_EXISTS("USER_ALREADY_EXISTS", "User already exists"),
    INVALID_REQUEST("INVALID_REQUEST", "Invalid request data"),
    UPDATE_FAILED("UPDATE_FAILED", "Failed to update user"),
    GET_USER_FAILED("GET_USER_FAILED", "Failed to retrieve user"),
    CHECK_FAILED("CHECK_FAILED", "Failed to check status"),
    BLOCK_FAILED("BLOCK_FAILED", "Failed to block user"),
    CREDENTIALS_INVALID("CREDENTIALS_INVALID", "Invalid credentials"),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}