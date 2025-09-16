package com.interview.challenge.shared.mapper;

import com.interview.challenge.shared.dto.*;
import com.interview.challenge.shared.model.OrderData;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * MapStruct mapper for automatic conversions
 * Simplified version without Map conversions (those will be handled manually)
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ArchitectMapper {

    // ========== ORDER MAPPINGS ==========

    /**
     * Convert OrderData to ArchitectOrderRequest
     * This works because both are structured DTOs
     */
    @Mapping(target = "clientId", source = "clientId")
    @Mapping(target = "symbol", source = "symbol")
    @Mapping(target = "side", source = "action")
    @Mapping(target = "quantity", source = "orderQty")
    @Mapping(target = "type", source = "orderType")
    @Mapping(target = "price", source = "limitPrice")
    @Mapping(target = "timeInForce", constant = "GTC")
    ArchitectOrderRequest toArchitectOrderRequest(com.interview.challenge.shared.model.OrderData orderData);

    // ========== HELPER METHODS ==========

    /**
     * Check if order status indicates success
     */
    default boolean isOrderSuccess(String status) {
        if (status == null) return false;
        return "FILLED".equalsIgnoreCase(status) ||
               "PARTIALLY_FILLED".equalsIgnoreCase(status);
    }

    /**
     * Check if order is filled
     */
    default boolean isOrderFilled(String status) {
        return "FILLED".equalsIgnoreCase(status);
    }

    /**
     * Normalize order action
     */
    @Named("normalizeAction")
    default String normalizeAction(String action) {
        if (action == null) return "BUY";
        switch (action.toUpperCase()) {
            case "SELL":
            case "SHORT":
                return "SELL";
            case "BUY":
            case "LONG":
            default:
                return "BUY";
        }
    }

    /**
     * Calculate stop loss price based on action and buffer
     */
    default BigDecimal calculateStopLossPrice(BigDecimal fillPrice, String action, BigDecimal stopLossPct, BigDecimal buffer) {
        if (fillPrice == null || stopLossPct == null) return null;

        BigDecimal stopLoss;
        if ("SELL".equalsIgnoreCase(action)) {
            // For sells, stop loss is above the fill price
            stopLoss = fillPrice.multiply(BigDecimal.ONE.add(stopLossPct));
        } else {
            // For buys, stop loss is below the fill price
            stopLoss = fillPrice.multiply(BigDecimal.ONE.subtract(stopLossPct));
        }

        // Apply buffer if provided
        if (buffer != null) {
            if ("SELL".equalsIgnoreCase(action)) {
                stopLoss = stopLoss.add(buffer);
            } else {
                stopLoss = stopLoss.subtract(buffer);
            }
        }

        return stopLoss.setScale(8, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Extract balance from ArchitectBalanceResponse
     */
    default BigDecimal extractBalance(ArchitectBalanceResponse balanceResponse) {
        return balanceResponse != null ? balanceResponse.getTotalBalance() : BigDecimal.ZERO;
    }

    /**
     * Extract daily PnL from ArchitectBalanceResponse
     */
    default BigDecimal extractDailyPnL(ArchitectBalanceResponse balanceResponse) {
        return balanceResponse != null ? balanceResponse.getRealizedPnl() : BigDecimal.ZERO;
    }

    /**
     * Extract total PnL from ArchitectBalanceResponse
     */
    default BigDecimal extractTotalPnL(ArchitectBalanceResponse balanceResponse) {
        return balanceResponse != null ? balanceResponse.getRealizedPnl() : BigDecimal.ZERO;
    }
}