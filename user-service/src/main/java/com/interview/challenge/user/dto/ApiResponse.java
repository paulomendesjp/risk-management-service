package com.interview.challenge.user.dto;

import com.interview.challenge.user.enums.ErrorCode;
import com.interview.challenge.user.enums.ResponseStatus;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class ApiResponse {

    private final boolean success;
    private final String status;
    private final String message;
    private final Map<String, Object> data;
    private final String error;
    private final LocalDateTime timestamp;

    private ApiResponse(Builder builder) {
        this.success = builder.success;
        this.status = builder.status;
        this.message = builder.message;
        this.data = builder.data;
        this.error = builder.error;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
    }

    public static Builder success() {
        return new Builder()
                .success(true)
                .status(ResponseStatus.SUCCESS.getValue());
    }

    public static Builder error(ErrorCode errorCode) {
        return new Builder()
                .success(false)
                .status(ResponseStatus.ERROR.getValue())
                .error(errorCode.getCode())
                .message(errorCode.getDefaultMessage());
    }

    public static Builder error() {
        return new Builder()
                .success(false)
                .status(ResponseStatus.ERROR.getValue());
    }

    // Getters
    public boolean isSuccess() {
        return success;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String getError() {
        return error;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    // Convert to Map for backward compatibility
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("success", success);
        if (message != null) map.put("message", message);
        if (error != null) map.put("error", error);
        if (data != null) map.putAll(data);
        map.put("timestamp", timestamp);
        return map;
    }

    public static class Builder {
        private boolean success;
        private String status;
        private String message;
        private Map<String, Object> data = new HashMap<>();
        private String error;
        private LocalDateTime timestamp;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder data(String key, Object value) {
            this.data.put(key, value);
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data.putAll(data);
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder errorCode(ErrorCode errorCode) {
            this.error = errorCode.getCode();
            this.message = errorCode.getDefaultMessage();
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ApiResponse build() {
            return new ApiResponse(this);
        }
    }
}