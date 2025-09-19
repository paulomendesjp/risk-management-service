package com.interview.challenge.kraken.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kraken Positions Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KrakenPositionsResponse {

    private String result;

    @JsonProperty("openPositions")
    private List<Position> openPositions;

    @JsonProperty("serverTime")
    private String serverTime;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Position {

        @JsonProperty("side")
        private String side; // "long" or "short"

        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("price")
        private BigDecimal price; // Average entry price

        @JsonProperty("fillTime")
        private String fillTime;

        @JsonProperty("size")
        private BigDecimal size;

        @JsonProperty("unrealizedFunding")
        private BigDecimal unrealizedFunding;

        @JsonProperty("pnl")
        private BigDecimal pnl;

        @JsonProperty("unrealizedPnl")
        private BigDecimal unrealizedPnl;

        @JsonProperty("realizedPnl")
        private BigDecimal realizedPnl;
    }

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(result);
    }

    public boolean hasOpenPositions() {
        return openPositions != null && !openPositions.isEmpty();
    }

    public int getPositionCount() {
        return openPositions != null ? openPositions.size() : 0;
    }
}