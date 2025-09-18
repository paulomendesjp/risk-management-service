package com.interview.challenge.risk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 📊 ACCOUNT MONITORING MODEL
 * 
 * Stores monitoring data for risk management
 */
@Document(collection = "account_monitoring")
public class AccountMonitoring {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String clientId;
    
    private BigDecimal initialBalance;
    private BigDecimal currentBalance;
    private BigDecimal dailyPnl;
    private BigDecimal totalPnl;
    private BigDecimal unrealizedPnl;
    
    private boolean dailyBlocked;
    private boolean permanentlyBlocked;
    private boolean realTimeMonitoring;
    
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastRiskCheck;
    private BigDecimal dailyStartBalance;
    private RiskStatus riskStatus;
    
    public AccountMonitoring() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.dailyPnl = BigDecimal.ZERO;
        this.totalPnl = BigDecimal.ZERO;
        this.unrealizedPnl = BigDecimal.ZERO;
        this.dailyBlocked = false;
        this.permanentlyBlocked = false;
        this.realTimeMonitoring = false;
        this.lastRiskCheck = LocalDateTime.now();
        this.dailyStartBalance = BigDecimal.ZERO;
        this.riskStatus = RiskStatus.NORMAL;
    }
    
    public AccountMonitoring(String clientId, BigDecimal initialBalance) {
        this();
        this.clientId = clientId;
        this.initialBalance = initialBalance;
        this.currentBalance = initialBalance;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    
    public BigDecimal getInitialBalance() { return initialBalance; }
    public void setInitialBalance(BigDecimal initialBalance) { this.initialBalance = initialBalance; }
    
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
    
    public BigDecimal getDailyPnl() { return dailyPnl; }
    public void setDailyPnl(BigDecimal dailyPnl) { this.dailyPnl = dailyPnl; }
    
    public BigDecimal getTotalPnl() { return totalPnl; }
    public void setTotalPnl(BigDecimal totalPnl) { this.totalPnl = totalPnl; }
    
    public BigDecimal getUnrealizedPnl() { return unrealizedPnl; }
    public void setUnrealizedPnl(BigDecimal unrealizedPnl) { this.unrealizedPnl = unrealizedPnl; }
    
    public boolean isDailyBlocked() { return dailyBlocked; }
    public void setDailyBlocked(boolean dailyBlocked) { this.dailyBlocked = dailyBlocked; }
    
    public boolean isPermanentlyBlocked() { return permanentlyBlocked; }
    public void setPermanentlyBlocked(boolean permanentlyBlocked) { this.permanentlyBlocked = permanentlyBlocked; }
    
    public boolean isRealTimeMonitoring() { return realTimeMonitoring; }
    public void setRealTimeMonitoring(boolean realTimeMonitoring) { this.realTimeMonitoring = realTimeMonitoring; }
    
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public LocalDateTime getLastRiskCheck() { return lastRiskCheck; }
    public void setLastRiskCheck(LocalDateTime lastRiskCheck) { this.lastRiskCheck = lastRiskCheck; }
    
    public BigDecimal getDailyStartBalance() { return dailyStartBalance; }
    public void setDailyStartBalance(BigDecimal dailyStartBalance) { this.dailyStartBalance = dailyStartBalance; }
    
    public RiskStatus getRiskStatus() { return riskStatus; }
    public void setRiskStatus(RiskStatus riskStatus) { this.riskStatus = riskStatus; }
    
    // Business Methods
    
    /**
     * Update balance and calculate PnL
     */
    public void updateBalance(BigDecimal newBalance, BigDecimal previousBalance) {
        this.currentBalance = newBalance;
        this.updatedAt = LocalDateTime.now();

        // Always calculate totalPnl from initialBalance
        if (this.initialBalance != null) {
            this.totalPnl = newBalance.subtract(this.initialBalance);
        }

        // Update daily PnL based on changes
        if (previousBalance != null) {
            BigDecimal change = newBalance.subtract(previousBalance);
            this.dailyPnl = this.dailyPnl.add(change);
        } else {
            // First update of the day - calculate from daily start
            if (this.dailyStartBalance != null) {
                this.dailyPnl = newBalance.subtract(this.dailyStartBalance);
            }
        }
    }
    
    /**
     * Check if account can trade
     */
    public boolean canTrade() {
        return !this.dailyBlocked && !this.permanentlyBlocked;
    }
    
    /**
     * Get current loss (negative PnL)
     */
    public BigDecimal getCurrentLoss() {
        return this.totalPnl.compareTo(BigDecimal.ZERO) < 0 ? this.totalPnl.abs() : BigDecimal.ZERO;
    }
    
    /**
     * Get daily loss (negative daily PnL)
     */
    public BigDecimal getDailyLoss() {
        return this.dailyPnl.compareTo(BigDecimal.ZERO) < 0 ? this.dailyPnl.abs() : BigDecimal.ZERO;
    }
    
    /**
     * Block account permanently
     */
    public void blockPermanently(String reason) {
        this.permanentlyBlocked = true;
        this.riskStatus = RiskStatus.MAX_RISK_TRIGGERED;
        this.lastError = reason;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Block account for the day
     */
    public void blockDaily(String reason) {
        this.dailyBlocked = true;
        this.riskStatus = RiskStatus.DAILY_RISK_TRIGGERED;
        this.lastError = reason;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Reset daily tracking (called at start of new day)
     */
    public void resetDailyTracking() {
        this.dailyBlocked = false;
        this.dailyPnl = BigDecimal.ZERO;
        this.dailyStartBalance = this.currentBalance;
        this.riskStatus = RiskStatus.NORMAL;
        this.updatedAt = LocalDateTime.now();
    }
}
