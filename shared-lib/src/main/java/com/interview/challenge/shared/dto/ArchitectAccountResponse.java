package com.interview.challenge.shared.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response from Architect API for account information
 */
public class ArchitectAccountResponse {
    private String accountId;
    private String accountName;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal marginUsed;
    private List<String> positions;

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public BigDecimal getMarginUsed() {
        return marginUsed;
    }

    public void setMarginUsed(BigDecimal marginUsed) {
        this.marginUsed = marginUsed;
    }

    public List<String> getPositions() {
        return positions;
    }

    public void setPositions(List<String> positions) {
        this.positions = positions;
    }
}