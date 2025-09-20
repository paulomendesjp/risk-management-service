package com.interview.challenge.shared.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 💰 BALANCE UPDATE EVENT
 * 
 * Event fired when account balance changes
 */
public class BalanceUpdateEvent {
    
    private String clientId;
    private String accountId;
    private BigDecimal newBalance;
    private BigDecimal previousBalance;
    private String source;
    private LocalDateTime timestamp;
    
    public BalanceUpdateEvent() {}
    
    public BalanceUpdateEvent(String clientId, BigDecimal newBalance) {
        this.clientId = clientId;
        this.newBalance = newBalance;
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters and Setters
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    
    public BigDecimal getNewBalance() { return newBalance; }
    public void setNewBalance(BigDecimal newBalance) { this.newBalance = newBalance; }
    
    public BigDecimal getPreviousBalance() { return previousBalance; }
    public void setPreviousBalance(BigDecimal previousBalance) { this.previousBalance = previousBalance; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}



