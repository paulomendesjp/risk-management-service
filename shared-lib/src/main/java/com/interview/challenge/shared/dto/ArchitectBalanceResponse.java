package com.interview.challenge.shared.dto;

import java.math.BigDecimal;

/**
 * DTO for Architect.co balance response
 */
public class ArchitectBalanceResponse {
    private BigDecimal totalBalance;
    private BigDecimal availableBalance;
    private BigDecimal balance; // Main balance field
    private BigDecimal realizedPnl;
    private BigDecimal unrealizedPnl;
    private BigDecimal totalPnl;
    private BigDecimal dailyPnl; // Daily P&L

    // Constructors
    public ArchitectBalanceResponse() {}

    // Getters and Setters
    public BigDecimal getTotalBalance() {
        return totalBalance;
    }

    public void setTotalBalance(BigDecimal totalBalance) {
        this.totalBalance = totalBalance;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public void setRealizedPnl(BigDecimal realizedPnl) {
        this.realizedPnl = realizedPnl;
    }

    public BigDecimal getUnrealizedPnl() {
        return unrealizedPnl;
    }

    public void setUnrealizedPnl(BigDecimal unrealizedPnl) {
        this.unrealizedPnl = unrealizedPnl;
    }

    public BigDecimal getTotalPnl() {
        return totalPnl;
    }

    public void setTotalPnl(BigDecimal totalPnl) {
        this.totalPnl = totalPnl;
    }

    public BigDecimal getBalance() {
        // Return balance or fallback to totalBalance
        return balance != null ? balance : totalBalance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getDailyPnl() {
        return dailyPnl != null ? dailyPnl : BigDecimal.ZERO;
    }

    public void setDailyPnl(BigDecimal dailyPnl) {
        this.dailyPnl = dailyPnl;
    }
}
