package com.interview.challenge.kraken.service;

import com.interview.challenge.kraken.client.KrakenApiClient;
import com.interview.challenge.kraken.client.KrakenAuthenticator;
import com.interview.challenge.kraken.dto.*;
import com.interview.challenge.kraken.exception.KrakenApiException;
import com.interview.challenge.kraken.model.KrakenAccount;
import com.interview.challenge.kraken.model.KrakenAccountMonitoring;
import com.interview.challenge.kraken.model.OrderTracking;
import com.interview.challenge.kraken.repository.KrakenAccountRepository;
import com.interview.challenge.kraken.repository.KrakenAccountMonitoringRepository;
import com.interview.challenge.kraken.repository.OrderTrackingRepository;
import com.interview.challenge.shared.event.BalanceUpdateEvent;
import com.interview.challenge.shared.event.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Kraken Trading Service
 *
 * Core business logic for Kraken trading operations
 */
@Slf4j
@Service
public class KrakenTradingService {

    @Autowired
    private KrakenApiClient krakenApiClient;

    @Autowired
    private KrakenAccountRepository accountRepository;

    @Autowired
    private OrderTrackingRepository orderTrackingRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private KrakenAccountMonitoringRepository accountMonitoringRepository;

    @Autowired
    private KrakenAuthenticator krakenAuthenticator;

    @Value("${kraken.trading.max-retry:3}")
    private int maxRetry;

    @Value("${kraken.trading.pyramid-max-orders:5}")
    private int pyramidMaxOrders;

    /**
     * Process webhook order from TradingView
     */
    public Map<String, Object> processWebhookOrder(KrakenOrderRequest orderRequest, String apiKey, String apiSecret) {
        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Processing webhook order for client: {}, strategy: {}",
                    orderRequest.getClientId(), orderRequest.getStrategy());

            // Validate credentials first
            if (!krakenAuthenticator.validateCredentials(apiKey, apiSecret)) {
                log.error("❌ Invalid Kraken API credentials for client: {}", orderRequest.getClientId());
                throw new IllegalArgumentException("Invalid Kraken API credentials");
            }

            // Validate order
            if (!orderRequest.isValid()) {
                throw new IllegalArgumentException("Invalid order request");
            }

            // Check if client is blocked due to risk violation
            checkRiskStatus(orderRequest.getClientId());

            // Check pyramid logic if enabled
            if (orderRequest.getPyramid() != null && !orderRequest.getPyramid()) {
                boolean hasSameDirection = checkPyramidViolation(
                    orderRequest.getClientId(),
                    orderRequest.getStrategy(),
                    orderRequest.getSide()
                );

                if (hasSameDirection) {
                    log.warn("Pyramid violation: Same direction order already exists for strategy: {}",
                            orderRequest.getStrategy());
                    response.put("success", false);
                    response.put("error", "PYRAMID_VIOLATION");
                    response.put("message", "Same direction order already exists for this strategy");
                    return response;
                }
            }

            // Check inverse logic if enabled
            if (orderRequest.getInverse() != null && orderRequest.getInverse()) {
                // Close existing position first
                closePositionForInverse(orderRequest.getSymbol(), apiKey, apiSecret);
            }

            // Place the main order
            KrakenOrderResponse orderResponse = krakenApiClient.placeOrder(orderRequest, apiKey, apiSecret);

            if (!orderResponse.isSuccess()) {
                throw new KrakenApiException("Order failed: " + orderResponse.getError());
            }

            // Track the order
            trackOrder(orderRequest, orderResponse.getOrderId());

            // Create stop loss if specified
            if (orderRequest.getStopLossPercentage() != null) {
                createStopLossOrder(orderRequest, orderResponse, apiKey, apiSecret);
            }

            // Check and update risk limits
            if (orderRequest.getMaxRiskPerDay() != null) {
                updateRiskLimits(orderRequest.getClientId(), orderRequest.getMaxRiskPerDay());
            }

            // Prepare successful response
            response.put("success", true);
            response.put("orderId", orderResponse.getOrderId());
            response.put("symbol", orderRequest.getSymbol());
            response.put("side", orderRequest.getSide());
            response.put("quantity", orderRequest.getOrderQty());
            response.put("timestamp", LocalDateTime.now());

            // Send notification
            publishOrderNotification(orderRequest, orderResponse);

            log.info("Order processed successfully: {}", orderResponse.getOrderId());

        } catch (Exception e) {
            log.error("Error processing webhook order: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getClass().getSimpleName());
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * Get account balance with caching
     */
    @Async
    public CompletableFuture<KrakenBalanceResponse> getAccountBalance(String clientId, String apiKey, String apiSecret) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Fetching balance for client: {}", clientId);

                KrakenBalanceResponse balance = krakenApiClient.getAccountBalance(apiKey, apiSecret);
                balance.setClientId(clientId);
                balance.setTimestamp(LocalDateTime.now());
                balance.setSource("kraken");

                // Publish balance update event
                publishBalanceUpdate(clientId, balance);

                return balance;

            } catch (Exception e) {
                log.error("Error fetching balance for client {}: {}", clientId, e.getMessage());
                throw new KrakenApiException("Failed to fetch balance", e);
            }
        });
    }

    /**
     * Close all positions for a client
     */
    public Map<String, Object> closeAllPositions(String clientId, String apiKey, String apiSecret) {
        try {
            log.info("Closing all positions for client: {}", clientId);

            // Validate credentials first
            if (!krakenAuthenticator.validateCredentials(apiKey, apiSecret)) {
                log.error("❌ Invalid Kraken API credentials for client: {}", clientId);
                throw new IllegalArgumentException("Invalid Kraken API credentials");
            }

            // Cancel all open orders first
            krakenApiClient.cancelAllOrders(apiKey, apiSecret, null);

            // Close all positions
            Map<String, Object> result = krakenApiClient.closeAllPositions(apiKey, apiSecret);

            // Update tracking
            orderTrackingRepository.markAllClosedForClient(clientId);

            // Send notification
            publishPositionClosedNotification(clientId, result);

            return result;

        } catch (Exception e) {
            log.error("Error closing positions for client {}: {}", clientId, e.getMessage());
            throw new KrakenApiException("Failed to close positions", e);
        }
    }

    /**
     * Check pyramid violation
     */
    private boolean checkPyramidViolation(String clientId, String strategy, String side) {
        if (strategy == null) return false;

        List<OrderTracking> activeOrders = orderTrackingRepository.findActiveOrdersByStrategy(clientId, strategy);

        // Check if any active order has the same direction
        return activeOrders.stream()
                .anyMatch(order -> order.getSide().equalsIgnoreCase(side));
    }

    /**
     * Close position for inverse trading
     */
    private void closePositionForInverse(String symbol, String apiKey, String apiSecret) {
        try {
            log.info("Closing position for inverse trading: {}", symbol);

            KrakenPositionsResponse positions = krakenApiClient.getOpenPositions(apiKey, apiSecret);

            if (positions.hasOpenPositions()) {
                for (KrakenPositionsResponse.Position position : positions.getOpenPositions()) {
                    if (position.getSymbol().equals(symbol)) {
                        // Create closing order
                        KrakenOrderRequest closeOrder = KrakenOrderRequest.builder()
                                .symbol(symbol)
                                .side(position.getSide().equalsIgnoreCase("long") ? "sell" : "buy")
                                .orderQty(position.getSize())
                                .orderType("mkt")
                                .reduceOnly(true)
                                .build();

                        krakenApiClient.placeOrder(closeOrder, apiKey, apiSecret);
                        log.info("Closed position for inverse: {}", symbol);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error closing position for inverse: {}", e.getMessage());
        }
    }

    /**
     * Create stop loss order
     */
    private void createStopLossOrder(KrakenOrderRequest originalOrder, KrakenOrderResponse orderResponse,
                                    String apiKey, String apiSecret) {
        try {
            // Get current market price (simplified - should fetch from market data)
            BigDecimal currentPrice = getEstimatedPrice(originalOrder.getSymbol(), apiKey, apiSecret);

            if (currentPrice == null) {
                log.warn("Cannot create stop loss - unable to get current price");
                return;
            }

            // Calculate stop price based on percentage
            BigDecimal stopPercentage = originalOrder.getStopLossPercentage().divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);
            BigDecimal stopPrice;

            if (originalOrder.getSide().equalsIgnoreCase("buy")) {
                // For long position, stop loss is below current price
                stopPrice = currentPrice.multiply(BigDecimal.ONE.subtract(stopPercentage));
            } else {
                // For short position, stop loss is above current price
                stopPrice = currentPrice.multiply(BigDecimal.ONE.add(stopPercentage));
            }

            KrakenOrderResponse stopResponse = krakenApiClient.createStopLossOrder(
                originalOrder.getKrakenSymbol(),
                originalOrder.getOrderQty(),
                stopPrice,
                apiKey,
                apiSecret
            );

            log.info("Stop loss order created: {} at price {}", stopResponse.getOrderId(), stopPrice);

        } catch (Exception e) {
            log.error("Error creating stop loss order: {}", e.getMessage());
        }
    }

    /**
     * Track order in database
     */
    private void trackOrder(KrakenOrderRequest orderRequest, String orderId) {
        OrderTracking tracking = new OrderTracking();
        tracking.setClientId(orderRequest.getClientId());
        tracking.setOrderId(orderId);
        tracking.setSymbol(orderRequest.getSymbol());
        tracking.setSide(orderRequest.getSide());
        tracking.setQuantity(orderRequest.getOrderQty());
        tracking.setStrategy(orderRequest.getStrategy());
        tracking.setStatus("ACTIVE");
        tracking.setCreatedAt(LocalDateTime.now());

        orderTrackingRepository.save(tracking);
    }

    /**
     * Update risk limits for client
     */
    private void updateRiskLimits(String clientId, BigDecimal maxRiskPerDay) {
        try {
            Optional<KrakenAccount> accountOpt = accountRepository.findByClientId(clientId);

            if (accountOpt.isPresent()) {
                KrakenAccount account = accountOpt.get();
                account.setMaxRiskPerDay(maxRiskPerDay);
                account.setUpdatedAt(LocalDateTime.now());
                accountRepository.save(account);
            }
        } catch (Exception e) {
            log.error("Error updating risk limits: {}", e.getMessage());
        }
    }

    /**
     * Get estimated price (simplified)
     */
    private BigDecimal getEstimatedPrice(String symbol, String apiKey, String apiSecret) {
        try {
            // In production, this would fetch actual market price
            // For now, using a simplified approach
            KrakenPositionsResponse positions = krakenApiClient.getOpenPositions(apiKey, apiSecret);

            if (positions.hasOpenPositions()) {
                return positions.getOpenPositions().stream()
                        .filter(p -> p.getSymbol().equals(symbol))
                        .findFirst()
                        .map(KrakenPositionsResponse.Position::getPrice)
                        .orElse(null);
            }

            return null;
        } catch (Exception e) {
            log.error("Error getting estimated price: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Publish balance update event
     */
    private void publishBalanceUpdate(String clientId, KrakenBalanceResponse balance) {
        try {
            BalanceUpdateEvent event = new BalanceUpdateEvent();
            event.setClientId(clientId);
            event.setNewBalance(balance.getTotalBalance());
            event.setSource("kraken");
            event.setTimestamp(LocalDateTime.now());

            rabbitTemplate.convertAndSend("balance.update.queue", event);
            log.debug("Published balance update for client: {}", clientId);

        } catch (Exception e) {
            log.error("Error publishing balance update: {}", e.getMessage());
        }
    }

    /**
     * Publish order notification
     */
    private void publishOrderNotification(KrakenOrderRequest order, KrakenOrderResponse response) {
        try {
            NotificationEvent event = NotificationEvent.orderPlaced(
                order.getClientId(),
                order.getSymbol(),
                order.getSide(),
                order.getOrderQty(),
                response.getOrderId()
            );

            rabbitTemplate.convertAndSend("notification.queue", event);

        } catch (Exception e) {
            log.error("Error publishing order notification: {}", e.getMessage());
        }
    }

    /**
     * Publish position closed notification
     */
    private void publishPositionClosedNotification(String clientId, Map<String, Object> result) {
        try {
            NotificationEvent event = NotificationEvent.positionsClosed(
                clientId,
                result.toString()
            );

            rabbitTemplate.convertAndSend("notification.queue", event);

        } catch (Exception e) {
            log.error("Error publishing position closed notification: {}", e.getMessage());
        }
    }

    /**
     * Check if client is blocked due to risk violation
     * Throws exception if client cannot trade
     */
    private void checkRiskStatus(String clientId) {
        KrakenAccountMonitoring monitoring = accountMonitoringRepository
            .findByClientId(clientId)
            .orElse(null);

        if (monitoring == null) {
            // No monitoring set up yet - allow trading but log warning
            log.warn("⚠️ No risk monitoring found for client: {}. Allowing trade.", clientId);
            return;
        }

        if (!monitoring.canTrade()) {
            String reason = "";
            if (monitoring.isPermanentBlocked()) {
                reason = "Account permanently blocked due to max risk violation: " +
                        monitoring.getPermanentBlockReason();
            } else if (monitoring.isDailyBlocked()) {
                reason = "Account blocked until 00:01 UTC due to daily risk violation: " +
                        monitoring.getDailyBlockReason();
            } else if (!monitoring.isActive()) {
                reason = "Account monitoring is not active";
            }

            log.error("🚫 Order rejected for client {}: {}", clientId, reason);
            throw new KrakenApiException("RISK_BLOCK: " + reason);
        }

        log.debug("✅ Risk check passed for client: {}", clientId);
    }
}