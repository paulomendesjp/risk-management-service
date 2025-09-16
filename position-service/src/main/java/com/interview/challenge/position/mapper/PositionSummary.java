package com.interview.challenge.position.mapper;


import java.math.BigDecimal;

/**
 * DTO for position summary information
 */
class PositionSummary {
    private final String clientId;
    private final int totalPositions;
    private final BigDecimal totalValue;
    private final BigDecimal totalLoss;

    public PositionSummary(String clientId, int totalPositions, BigDecimal totalValue, BigDecimal totalLoss) {
        this.clientId = clientId;
        this.totalPositions = totalPositions;
        this.totalValue = totalValue;
        this.totalLoss = totalLoss;
    }

    // Getters
    public String getClientId() { return clientId; }
    public int getTotalPositions() { return totalPositions; }
    public BigDecimal getTotalValue() { return totalValue; }
    public BigDecimal getTotalLoss() { return totalLoss; }
}
