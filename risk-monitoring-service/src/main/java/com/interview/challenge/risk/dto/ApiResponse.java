package com.interview.challenge.risk.dto;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class ApiResponse {

    private final String status;
    private final Map<String, Object> data;
    private final LocalDateTime timestamp;
    private final String message;

    private ApiResponse(Builder builder) {
        this.status = builder.status;
        this.data = builder.data;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.message = builder.message;
    }

    public static Builder success() {
        return new Builder().status("success");
    }

    public static Builder error() {
        return new Builder().status("error");
    }

    public static Builder status(String status) {
        return new Builder().status(status);
    }

    // Getters
    public String getStatus() {
        return status;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }

    public static class Builder {
        private String status;
        private Map<String, Object> data = new HashMap<>();
        private LocalDateTime timestamp;
        private String message;

        public Builder status(String status) {
            this.status = status;
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

        public Builder message(String message) {
            this.message = message;
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