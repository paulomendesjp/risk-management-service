package com.interview.challenge.kraken.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Kraken Account Model for MongoDB
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "kraken_accounts")
public class KrakenAccount {

    @Id
    private String id;

    @Indexed(unique = true)
    private String clientId;

    private String apiKey; // Encrypted

    private String apiSecret; // Encrypted

    private BigDecimal initialBalance;

    private BigDecimal currentBalance;

    private BigDecimal maxRiskPerDay; // Percentage

    private BigDecimal dailyLoss;

    private LocalDateTime dailyResetTime;

    private Boolean tradingEnabled;

    private Boolean dailyBlocked;

    private String status; // ACTIVE, BLOCKED, SUSPENDED

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastBalanceCheck;

    // Trading statistics
    private Integer totalOrders;

    private Integer openPositions;

    private BigDecimal totalPnl;

    private BigDecimal realizedPnl;

    private BigDecimal unrealizedPnl;

    // Risk tracking
    private BigDecimal maxDrawdown;

    private LocalDateTime maxDrawdownTime;

    private Integer riskViolationCount;

    private LocalDateTime lastRiskViolation;

    public KrakenAccount(String clientId) {
        this.clientId = clientId;
        this.tradingEnabled = true;
        this.dailyBlocked = false;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.totalOrders = 0;
        this.openPositions = 0;
        this.riskViolationCount = 0;
    }
}