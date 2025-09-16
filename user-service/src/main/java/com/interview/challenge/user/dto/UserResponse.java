package com.interview.challenge.user.dto;

import com.interview.challenge.shared.model.RiskLimit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UserResponse {

    private String clientId;
    private BigDecimal initialBalance;
    private RiskLimit maxRisk;
    private RiskLimit dailyRisk;
    private boolean dailyBlocked;
    private boolean permanentBlocked;
    private boolean canTrade;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructors
    public UserResponse() {
    }

    public UserResponse(String clientId, BigDecimal initialBalance, RiskLimit maxRisk, RiskLimit dailyRisk) {
        this.clientId = clientId;
        this.initialBalance = initialBalance;
        this.maxRisk = maxRisk;
        this.dailyRisk = dailyRisk;
    }

    // Builder pattern
    public static UserResponseBuilder builder() {
        return new UserResponseBuilder();
    }

    // Getters and Setters
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }

    public RiskLimit getMaxRisk() {
        return maxRisk;
    }

    public void setMaxRisk(RiskLimit maxRisk) {
        this.maxRisk = maxRisk;
    }

    public RiskLimit getDailyRisk() {
        return dailyRisk;
    }

    public void setDailyRisk(RiskLimit dailyRisk) {
        this.dailyRisk = dailyRisk;
    }

    public boolean isDailyBlocked() {
        return dailyBlocked;
    }

    public void setDailyBlocked(boolean dailyBlocked) {
        this.dailyBlocked = dailyBlocked;
    }

    public boolean isPermanentBlocked() {
        return permanentBlocked;
    }

    public void setPermanentBlocked(boolean permanentBlocked) {
        this.permanentBlocked = permanentBlocked;
    }

    public boolean isCanTrade() {
        return canTrade;
    }

    public void setCanTrade(boolean canTrade) {
        this.canTrade = canTrade;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Builder class
    public static class UserResponseBuilder {
        private String clientId;
        private BigDecimal initialBalance;
        private RiskLimit maxRisk;
        private RiskLimit dailyRisk;
        private boolean dailyBlocked;
        private boolean permanentBlocked;
        private boolean canTrade;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public UserResponseBuilder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public UserResponseBuilder initialBalance(BigDecimal initialBalance) {
            this.initialBalance = initialBalance;
            return this;
        }

        public UserResponseBuilder maxRisk(RiskLimit maxRisk) {
            this.maxRisk = maxRisk;
            return this;
        }

        public UserResponseBuilder dailyRisk(RiskLimit dailyRisk) {
            this.dailyRisk = dailyRisk;
            return this;
        }

        public UserResponseBuilder dailyBlocked(boolean dailyBlocked) {
            this.dailyBlocked = dailyBlocked;
            return this;
        }

        public UserResponseBuilder permanentBlocked(boolean permanentBlocked) {
            this.permanentBlocked = permanentBlocked;
            return this;
        }

        public UserResponseBuilder canTrade(boolean canTrade) {
            this.canTrade = canTrade;
            return this;
        }

        public UserResponseBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public UserResponseBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public UserResponse build() {
            UserResponse response = new UserResponse();
            response.clientId = this.clientId;
            response.initialBalance = this.initialBalance;
            response.maxRisk = this.maxRisk;
            response.dailyRisk = this.dailyRisk;
            response.dailyBlocked = this.dailyBlocked;
            response.permanentBlocked = this.permanentBlocked;
            response.canTrade = this.canTrade;
            response.createdAt = this.createdAt;
            response.updatedAt = this.updatedAt;
            return response;
        }
    }
}