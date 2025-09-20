package com.interview.challenge.kraken.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Kraken Balance Response DTO
 *
 * Represents account balance information from Kraken Futures API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KrakenBalanceResponse {

    private String result; // "success" or error message
    private boolean success; // Added for compatibility

    @JsonProperty("serverTime")
    private String serverTime;

    // Account balances
    @JsonProperty("accounts")
    private Map<String, AccountBalance> accounts;

    private BigDecimal totalBalance;
    private BigDecimal availableBalance;
    private BigDecimal marginBalance;
    private BigDecimal unrealizedPnl;
    private BigDecimal realizedPnl;

    // Additional fields for risk monitoring
    private String clientId;
    private LocalDateTime timestamp;
    private String source; // "kraken"

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AccountBalance {

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("balance")
        private BigDecimal balance;

        @JsonProperty("availableBalance")
        private BigDecimal availableBalance;

        @JsonProperty("initialMargin")
        private BigDecimal initialMargin;

        @JsonProperty("maintenanceMargin")
        private BigDecimal maintenanceMargin;

        @JsonProperty("unrealizedPnl")
        private BigDecimal unrealizedPnl;

        @JsonProperty("realizedPnl")
        private BigDecimal realizedPnl;

        @JsonProperty("portfolioValue")
        private BigDecimal portfolioValue;

        @JsonProperty("marginBalance")
        private BigDecimal marginBalance;
    }

    /**
     * Calculate total balance from all accounts
     */
    public BigDecimal calculateTotalBalance() {
        if (accounts == null || accounts.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return accounts.values().stream()
                .map(AccountBalance::getBalance)
                .filter(balance -> balance != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate total available balance
     */
    public BigDecimal calculateAvailableBalance() {
        if (accounts == null || accounts.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return accounts.values().stream()
                .map(AccountBalance::getAvailableBalance)
                .filter(balance -> balance != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate total unrealized PnL
     */
    public BigDecimal calculateUnrealizedPnl() {
        if (accounts == null || accounts.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return accounts.values().stream()
                .map(AccountBalance::getUnrealizedPnl)
                .filter(pnl -> pnl != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get main account balance (usually USD)
     */
    public AccountBalance getMainAccount() {
        if (accounts == null) return null;

        // Try to find USD account first
        AccountBalance usdAccount = accounts.get("fi_usd");
        if (usdAccount != null) return usdAccount;

        // Return first available account
        return accounts.values().stream().findFirst().orElse(null);
    }

    /**
     * Check if response is successful
     */
    public boolean isSuccess() {
        return "success".equalsIgnoreCase(result);
    }

    // Manual getters/setters for backup
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getServerTime() { return serverTime; }
    public void setServerTime(String serverTime) { this.serverTime = serverTime; }

    public Map<String, AccountBalance> getAccounts() { return accounts; }
    public void setAccounts(Map<String, AccountBalance> accounts) { this.accounts = accounts; }

    public BigDecimal getTotalBalance() { return totalBalance; }
    public void setTotalBalance(BigDecimal totalBalance) { this.totalBalance = totalBalance; }

    public BigDecimal getAvailableBalance() { return availableBalance; }
    public void setAvailableBalance(BigDecimal availableBalance) { this.availableBalance = availableBalance; }

    public BigDecimal getMarginBalance() { return marginBalance; }
    public void setMarginBalance(BigDecimal marginBalance) { this.marginBalance = marginBalance; }

    public BigDecimal getUnrealizedPnl() { return unrealizedPnl; }
    public void setUnrealizedPnl(BigDecimal unrealizedPnl) { this.unrealizedPnl = unrealizedPnl; }

    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}