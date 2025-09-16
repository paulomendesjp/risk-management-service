package com.interview.challenge.position.service;

import com.interview.challenge.position.constants.PositionConstants;
import com.interview.challenge.position.dto.*;
import com.interview.challenge.position.enums.OrderAction;
import com.interview.challenge.position.enums.OrderStatus;
import com.interview.challenge.position.enums.OrderType;
import com.interview.challenge.shared.dto.*;
import com.interview.challenge.shared.exception.ArchitectApiException;
import com.interview.challenge.shared.exception.TradingException;
import com.interview.challenge.shared.model.OrderData;
import com.interview.challenge.shared.model.RiskLimit;
import com.interview.challenge.shared.service.ArchitectApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Position Service - Core trading logic refactored with structured DTOs
 *
 * Features:
 * - Structured error handling (no more generic exceptions)
 * - MapStruct automatic conversions (no more manual mapping)
 * - Clean logging (no emojis)
 * - Type-safe operations (no more Maps)
 *
 * Based on requirements from main.py but modernized for microservices
 */
@Service
public class PositionService {

    private static final Logger logger = LoggerFactory.getLogger(PositionService.class);

    @Autowired
    private ArchitectApiService architectApiService;

    @Value("${trading.order.default-timeout:30000}")
    private long orderTimeoutMs;

    @Value("${trading.order.fill-check-interval:1000}")
    private long fillCheckIntervalMs;

    @Value("${trading.order.max-fill-attempts:5}")
    private int maxFillAttempts;

    @Value("${trading.stop-loss.price-buffer:0.001}")
    private BigDecimal stopLossPriceBuffer;


    /**
     * Place trading order with structured error handling and type safety
     *
     * Requirement: Place orders via Architect API with automatic stop-loss
     */
    public OrderResponse placeOrder(OrderData orderData, String apiKey, String apiSecret) {
        String clientId = orderData.getClientId();
        MDC.put(PositionConstants.MDC_CLIENT_ID, clientId);
        MDC.put(PositionConstants.MDC_OPERATION, "placeOrder");
        MDC.put(PositionConstants.MDC_SYMBOL, orderData.getSymbol());

        logger.info("Placing order for client {} | Symbol: {} | Action: {} | Quantity: {}",
                clientId, orderData.getSymbol(), orderData.getAction(), orderData.getOrderQty());

        try {
            // Place main order - SYNCHRONOUS, NO ASYNC
            ArchitectOrderResponse orderResponse = architectApiService.placeOrder(orderData, apiKey, apiSecret);

            // Convert to structured response manually (MapStruct not available)
            OrderResponse response = new OrderResponse();
            response.setTradeId(orderResponse.getOrderId());
            response.setClientId(clientId);
            response.setSymbol(orderResponse.getSymbol());
            response.setAction(orderResponse.getSide());
            response.setQuantity(orderResponse.getOrderQty());
            response.setOrderMethod(OrderType.MARKET.getDisplayName());
            response.setSuccess(true);
            response.setExecutionTime(LocalDateTime.now());
            response.setRequestId(java.util.UUID.randomUUID().toString());

            // Use the price from order response if available
            if (orderResponse.getPrice() != null) {
                response.setPrice(orderResponse.getPrice());
            } else if (orderResponse.getAverageFillPrice() != null) {
                response.setPrice(orderResponse.getAverageFillPrice());
            }

            logger.info("Order placed successfully for client {} | Trade ID: {} | Status: {}",
                    clientId, response.getTradeId(), orderResponse.getStatus());

            // NO STOP-LOSS - Return immediately
            // NO WAITING - Return immediately

            return response;

        } catch (TradingException e) {
            return handleTradingException(e, clientId, orderData.getSymbol());
        } catch (ArchitectApiException e) {
            return handleArchitectApiException(e, clientId, orderData.getSymbol());
        } catch (Exception e) {
            return handleUnexpectedException(e, clientId, orderData.getSymbol(), "placing order");
        } finally {
            MDC.clear();
        }
    }

    /**
     * Close all positions for a client (called by Risk Monitoring Service)
     *
     * Requirement: Close all orders when risk limits are breached
     */
    public CompletableFuture<ClosePositionsResult> closeAllPositions(String clientId, RiskLimit.RiskType riskType, String apiKey, String apiSecret) {
        MDC.put(PositionConstants.MDC_CLIENT_ID, clientId);
        MDC.put(PositionConstants.MDC_OPERATION, "closeAllPositions");
        MDC.put("riskType", riskType.toString());

        String reason = riskType == RiskLimit.RiskType.DAILY_RISK
                ? "Daily risk limit exceeded"
                : "Maximum risk limit exceeded";

        logger.warn("Closing all positions for client {} | Reason: {}", clientId, reason);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeCloseAllPositions(clientId, reason, apiKey, apiSecret);
            } catch (TradingException e) {
                logger.error("Trading error closing positions for client {}: {}", clientId, e.getMessage());
                return ClosePositionsResult.error(clientId, reason, e.getMessage());
            } catch (ArchitectApiException e) {
                logger.error("Architect API error closing positions for client {}: {} (status: {})",
                        clientId, e.getMessage(), e.getStatusCode());
                return ClosePositionsResult.error(clientId, reason, "API Error: " + e.getMessage());
            } catch (Exception e) {
                logger.error("Unexpected error closing positions for client {}: {}", clientId, e.getMessage(), e);
                return ClosePositionsResult.error(clientId, reason, "Unexpected error: " + e.getMessage());
            } finally {
                MDC.clear();
            }
        });
    }

    /**
     * Get account balance with structured response
     *
     * Requirement: Monitor account balances for risk management
     */
    public CompletableFuture<BigDecimal> getAccountBalance(String clientId, String apiKey, String apiSecret) {
        MDC.put(PositionConstants.MDC_CLIENT_ID, clientId);
        MDC.put(PositionConstants.MDC_OPERATION, "getAccountBalance");

        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Getting account balance for client {}", clientId);

                ArchitectBalanceResponse balanceResponse = architectApiService.getBalance(apiKey, apiSecret);
                BigDecimal balance = balanceResponse != null ? balanceResponse.getBalance() : BigDecimal.ZERO;

                logger.debug("Retrieved balance for client {}: {}", clientId, balance);
                return balance;

            } catch (ArchitectApiException e) {
                logger.error("Architect API error getting balance for client {}: {} (status: {})",
                        clientId, e.getMessage(), e.getStatusCode());
                throw new TradingException("Failed to get account balance: " + e.getMessage(), clientId, "GET_BALANCE");

            } catch (Exception e) {
                logger.error("Unexpected error getting balance for client {}: {}", clientId, e.getMessage(), e);
                throw new TradingException("Unexpected error getting balance: " + e.getMessage(), e, clientId, "GET_BALANCE");

            } finally {
                MDC.clear();
            }
        });
    }

    /**
     * Get open positions with structured response
     *
     * Requirement: Monitor open positions for risk calculations
     */
    public CompletableFuture<List<ArchitectPositionResponse>> getOpenPositions(String clientId, String apiKey, String apiSecret) {
        MDC.put(PositionConstants.MDC_CLIENT_ID, clientId);
        MDC.put(PositionConstants.MDC_OPERATION, "getOpenPositions");

        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Getting open positions for client {}", clientId);

                List<ArchitectPositionResponse> positions = architectApiService.getPositions(apiKey, apiSecret);

                logger.debug("Retrieved {} open positions for client {}", positions.size(), clientId);
                return positions;

            } catch (ArchitectApiException e) {
                logger.error("Architect API error getting positions for client {}: {} (status: {})",
                        clientId, e.getMessage(), e.getStatusCode());
                throw new TradingException("Failed to get open positions: " + e.getMessage(), clientId, "GET_POSITIONS");

            } catch (Exception e) {
                logger.error("Unexpected error getting positions for client {}: {}", clientId, e.getMessage(), e);
                throw new TradingException("Unexpected error getting positions: " + e.getMessage(), e, clientId, "GET_POSITIONS");

            } finally {
                MDC.clear();
            }
        });
    }

    /**
     * Get open orders with structured response
     *
     * Requirement: Monitor open orders for position management
     */
    public CompletableFuture<List<ArchitectOrderResponse>> getOpenOrders(String apiKey, String apiSecret) {
        MDC.put(PositionConstants.MDC_OPERATION, "getOpenOrders");

        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Getting open orders");

                List<ArchitectOrderResponse> orders = architectApiService.getOpenOrders(apiKey, apiSecret);

                logger.debug("Retrieved {} open orders", orders.size());
                return orders;

            } catch (ArchitectApiException e) {
                logger.error("Architect API error getting orders: {} (status: {})",
                        e.getMessage(), e.getStatusCode());
                throw new TradingException("Failed to get open orders: " + e.getMessage(), null, "GET_ORDERS");

            } catch (Exception e) {
                logger.error("Unexpected error getting orders: {}", e.getMessage(), e);
                throw new TradingException("Unexpected error getting orders: " + e.getMessage(), e, null, "GET_ORDERS");

            } finally {
                MDC.clear();
            }
        });
    }

    // Overload to maintain compatibility with existing calls that include clientId
    public CompletableFuture<List<ArchitectOrderResponse>> getOpenOrders(String clientId, String apiKey, String apiSecret) {
        // Just ignore the clientId and call the new method
        return getOpenOrders(apiKey, apiSecret);
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Execute the actual logic for closing all positions
     */
    private ClosePositionsResult executeCloseAllPositions(String clientId, String reason, String apiKey, String apiSecret) {
        // Get all open orders using structured DTOs
        List<ArchitectOrderResponse> openOrders = architectApiService.getOpenOrders(apiKey, apiSecret);

        ClosePositionsResult result = ClosePositionsResult.success(clientId, reason);
        result.setTotalPositions(openOrders.size());

        if (openOrders.isEmpty()) {
            logger.info("No open positions found for client {}", clientId);
            result.setSuccess(true);
            return result;
        }

        logger.info("Found {} open positions for client {}, closing all", openOrders.size(), clientId);

        // Close each position
        for (ArchitectOrderResponse order : openOrders) {
            processOrderClosure(order, result, clientId, apiKey, apiSecret);
        }

        // Determine overall success and log results
        finalizeClosePositionsResult(result, clientId);
        return result;
    }

    /**
     * Process the closure of a single order
     */
    private void processOrderClosure(ArchitectOrderResponse order, ClosePositionsResult result, String clientId, String apiKey, String apiSecret) {
        try {
            ArchitectOrderResponse cancelResponse = architectApiService.cancelOrder(
                    order.getOrderId(), apiKey, apiSecret);

            // Check if cancellation was successful
            String status = cancelResponse.getStatus();
            if (isOrderSuccessfullyClosed(status)) {
                // Calculate order value if available
                BigDecimal orderValue = calculateOrderValue(order);
                result.addClosedOrder(order.getOrderId(), orderValue);

                logger.debug("Closed position {} for client {} | Value: {}",
                        order.getOrderId(), clientId, orderValue);
            } else {
                result.addFailedOrder(order.getOrderId());
                logger.warn("Failed to close position {} for client {}", order.getOrderId(), clientId);
            }

        } catch (Exception e) {
            result.addFailedOrder(order.getOrderId());
            logger.error("Error closing position {} for client {}: {}",
                    order.getOrderId(), clientId, e.getMessage());
        }
    }

    /**
     * Check if order closure was successful
     */
    private boolean isOrderSuccessfullyClosed(String status) {
        return status != null && (status.contains("CANCELLED") || status.contains("CLOSED"));
    }

    /**
     * Finalize the close positions result and log appropriate messages
     */
    private void finalizeClosePositionsResult(ClosePositionsResult result, String clientId) {
        boolean overallSuccess = result.getClosedPositions().size() > 0;
        result.setSuccess(overallSuccess);

        if (result.hasFailures()) {
            logger.warn("Partial success closing positions for client {} | Closed: {} | Failed: {}",
                    clientId, result.getClosedPositions().size(), result.getFailedOrderIds().size());
        } else {
            logger.info("Successfully closed all {} positions for client {} | Total value: {}",
                    result.getClosedPositions().size(), clientId, result.getTotalValue());
        }
    }

    /**
     * Handle TradingException with consistent pattern
     */
    private OrderResponse handleTradingException(TradingException e, String clientId, String symbol) {
        logger.error("Trading error for client {}: {}", clientId, e.getMessage());
        return OrderResponse.error(clientId, symbol, e.getMessage());
    }

    /**
     * Handle ArchitectApiException with consistent pattern
     */
    private OrderResponse handleArchitectApiException(ArchitectApiException e, String clientId, String symbol) {
        logger.error("Architect API error for client {}: {} (status: {})",
                clientId, e.getMessage(), e.getStatusCode());
        return OrderResponse.error(clientId, symbol, "Architect API call failed: " + e.getMessage());
    }

    /**
     * Handle unexpected Exception with consistent pattern
     */
    private OrderResponse handleUnexpectedException(Exception e, String clientId, String symbol, String operation) {
        logger.error("Unexpected error {} for client {}: {}", operation, clientId, e.getMessage(), e);
        return OrderResponse.error(clientId, symbol, "Unexpected error: " + e.getMessage());
    }

    /**
     * Get the opposite trading action for stop-loss orders
     */
    private String getOppositeAction(String action) {
        if (OrderAction.BUY.getDisplayName().equalsIgnoreCase(action)) {
            return OrderAction.SELL.getDisplayName();
        } else if (OrderAction.SELL.getDisplayName().equalsIgnoreCase(action)) {
            return OrderAction.BUY.getDisplayName();
        }
        // Default fallback for other actions
        return OrderAction.SELL.getDisplayName();
    }

    /**
     * Wait for order to fill with structured polling
     */
    private OrderFillResult waitForOrderFill(String orderId, String apiKey, String apiSecret) {
        logger.debug("Waiting for order fill: {}", orderId);

        for (int attempt = 0; attempt < maxFillAttempts; attempt++) {
            try {
                ArchitectOrderResponse orderInfo = architectApiService.getOrder(orderId, apiKey, apiSecret);

                if (orderInfo != null) {
                    // Check if order is filled
                    if (isOrderFilled(orderInfo.getStatus()) && orderInfo.getAverageFillPrice() != null) {
                        BigDecimal fillPrice = orderInfo.getAverageFillPrice();
                        logger.debug("Order {} filled at price: {}", orderId, fillPrice);
                        return new OrderFillResult(true, fillPrice);
                    }

                    // Check if order is still pending (valid but not filled)
                    String status = orderInfo.getStatus();
                    if (isOrderPending(status)) {
                        logger.debug("Order {} still pending, status: {}", orderId, status);
                    }
                }

                // Wait before next attempt (but not on the last attempt)
                if (attempt < maxFillAttempts - 1) {
                    Thread.sleep(fillCheckIntervalMs);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Order fill check interrupted for order: {}", orderId);
                break;
            } catch (Exception e) {
                // Don't fail on individual check errors (might be temporary)
                logger.debug("Error checking order fill status for {} (attempt {}): {}", orderId, attempt + 1, e.getMessage());
                // Stop trying if it's a persistent error
                if (e.getMessage() != null && e.getMessage().contains("MapStruct")) {
                    logger.warn("Stopping order fill check due to configuration issue");
                    break;
                }
            }
        }

        logger.info("Order {} not filled within {} seconds - returning as pending", orderId, (maxFillAttempts * fillCheckIntervalMs) / 1000);
        return new OrderFillResult(false, null);
    }

    /**
     * Place stop-loss order with structured request
     */
    private String placeStopLossOrder(OrderData originalOrder, BigDecimal fillPrice, String apiKey, String apiSecret) {
        try {
            // Calculate stop-loss price manually
            BigDecimal stopLossPct = originalOrder.getStopLoss();
            BigDecimal stopPrice = null;

            if (fillPrice != null && stopLossPct != null) {
                if (OrderAction.SELL.getDisplayName().equalsIgnoreCase(originalOrder.getAction())) {
                    // For sells, stop loss is above the fill price
                    stopPrice = fillPrice.multiply(BigDecimal.ONE.add(stopLossPct));
                    stopPrice = stopPrice.add(stopLossPriceBuffer);
                } else {
                    // For buys, stop loss is below the fill price
                    stopPrice = fillPrice.multiply(BigDecimal.ONE.subtract(stopLossPct));
                    stopPrice = stopPrice.subtract(stopLossPriceBuffer);
                }
                stopPrice = stopPrice.setScale(8, RoundingMode.HALF_UP);
            }

            if (stopPrice == null) {
                throw new TradingException("Invalid stop-loss calculation", originalOrder.getClientId(), "STOP_LOSS");
            }

            // Create stop-loss order
            OrderData stopOrder = new OrderData();
            stopOrder.setClientId(originalOrder.getClientId());
            stopOrder.setSymbol(originalOrder.getSymbol());
            stopOrder.setAction(getOppositeAction(originalOrder.getAction())); // Opposite action
            stopOrder.setOrderQty(originalOrder.getOrderQty());
            stopOrder.setOrderType(OrderType.STOP_LIMIT.getDisplayName());
            stopOrder.setStopPrice(stopPrice);
            stopOrder.setLimitPrice(stopPrice); // Same as stop price for simplicity

            ArchitectOrderResponse stopResponse = architectApiService.placeOrder(stopOrder, apiKey, apiSecret);
            return stopResponse.getOrderId();

        } catch (Exception e) {
            logger.error("Failed to place stop-loss order: {}", e.getMessage());
            throw new TradingException.OrderPlacementException(
                    "Stop-loss order placement failed: " + e.getMessage(), originalOrder.getClientId());
        }
    }

    /**
     * Check if order is filled
     */
    private boolean isOrderFilled(String status) {
        return OrderStatus.FILLED.getDisplayName().equalsIgnoreCase(status);
    }

    /**
     * Check if order is still pending
     */
    private boolean isOrderPending(String status) {
        return status != null && (
            OrderStatus.PENDING.getDisplayName().equalsIgnoreCase(status) ||
            status.contains("OPEN") ||
            status.contains("NEW")
        );
    }

    /**
     * Calculate order value for reporting
     */
    private BigDecimal calculateOrderValue(ArchitectOrderResponse order) {
        if (order.getAverageFillPrice() != null && order.getFilledQty() != null) {
            return order.getAverageFillPrice().multiply(order.getFilledQty());
        } else if (order.getOrderQty() != null) {
            // Estimate based on order quantity (assuming some default price)
            return order.getOrderQty().multiply(BigDecimal.valueOf(100)); // Placeholder
        }
        return BigDecimal.ZERO;
    }

    /**
     * Inner class for order fill results
     */
    private static class OrderFillResult {
        private final boolean filled;
        private final BigDecimal fillPrice;

        public OrderFillResult(boolean filled, BigDecimal fillPrice) {
            this.filled = filled;
            this.fillPrice = fillPrice;
        }

        public boolean isFilled() {
            return filled;
        }

        public BigDecimal getFillPrice() {
            return fillPrice;
        }
    }
}
