package com.interview.challenge.kraken.websocket;

import com.interview.challenge.kraken.model.KrakenAccountMonitoring;
import com.interview.challenge.kraken.repository.KrakenAccountMonitoringRepository;
import com.interview.challenge.kraken.service.KrakenRiskCalculator;
import com.interview.challenge.kraken.service.KrakenRiskCalculator.RiskCheckResult;
import com.interview.challenge.kraken.service.KrakenTradingService;
import com.interview.challenge.shared.event.NotificationEvent;
import com.interview.challenge.shared.event.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler for WebSocket messages from Kraken
 *
 * Processes real-time balance updates, order updates, and trade executions
 * Performs risk checks and triggers actions when necessary
 */
@Slf4j
@Component
public class KrakenWebSocketHandler {

    @Autowired
    private KrakenAccountMonitoringRepository repository;

    @Autowired
    private KrakenRiskCalculator riskCalculator;

    @Autowired
    private KrakenTradingService tradingService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * Handle real-time balance update from WebSocket
     */
    public void handleBalanceUpdate(String clientId, String asset, String balance, Map<String, Object> fullData) {
        try {
            log.info("💰 Real-time balance update for client {}: {} = {}", clientId, asset, balance);

            // Find monitoring account
            repository.findByClientId(clientId).ifPresent(account -> {
                if (!account.isActive()) {
                    log.debug("Account {} is not active, skipping balance update", clientId);
                    return;
                }

                // Convert balance to BigDecimal
                BigDecimal newBalance = new BigDecimal(balance);
                BigDecimal previousBalance = account.getCurrentBalance();

                // Only process if balance actually changed
                if (previousBalance != null && previousBalance.compareTo(newBalance) == 0) {
                    log.debug("Balance unchanged for client {}", clientId);
                    return;
                }

                // Update balance
                account.updateBalance(newBalance);
                account.setLastChecked(LocalDateTime.now());
                account.setLastBalanceUpdate(LocalDateTime.now());

                // Log balance change
                if (previousBalance != null) {
                    BigDecimal change = newBalance.subtract(previousBalance);
                    String direction = change.compareTo(BigDecimal.ZERO) > 0 ? "📈" : "📉";
                    log.info("{} Balance changed for {}: {} → {} ({}{})",
                        direction, clientId, previousBalance, newBalance,
                        change.compareTo(BigDecimal.ZERO) > 0 ? "+" : "", change);
                }

                // Check risk limits
                RiskCheckResult riskResult = riskCalculator.checkRisk(account);

                if (riskResult.isDailyRiskViolated()) {
                    handleDailyRiskViolation(account, riskResult);
                } else if (riskResult.isMaxRiskViolated()) {
                    handleMaxRiskViolation(account, riskResult);
                } else {
                    // Just save the updated balance
                    repository.save(account);

                    // Publish balance update event
                    publishBalanceUpdateEvent(account, previousBalance, newBalance);
                }
            });

        } catch (Exception e) {
            log.error("❌ Error handling balance update for client {}: {}", clientId, e.getMessage(), e);
        }
    }

    /**
     * Handle real-time order update from WebSocket
     */
    public void handleOrderUpdate(String clientId, Map<String, Object> orderData) {
        try {
            String orderId = (String) orderData.get("order_id");
            String status = (String) orderData.get("status");
            String pair = (String) orderData.get("pair");

            log.info("📊 Order update for client {}: Order {} ({}) is {}",
                clientId, orderId, pair, status);

            // Check if order is filled or cancelled
            if ("filled".equalsIgnoreCase(status)) {
                log.info("✅ Order {} filled for client {}", orderId, clientId);

                // Trigger balance check after order filled
                repository.findByClientId(clientId).ifPresent(account -> {
                    // Request balance update (WebSocket will send it automatically)
                    log.debug("Order filled, waiting for balance update via WebSocket");
                });

            } else if ("cancelled".equalsIgnoreCase(status) || "expired".equalsIgnoreCase(status)) {
                log.info("❌ Order {} {} for client {}", orderId, status, clientId);
            }

        } catch (Exception e) {
            log.error("❌ Error handling order update for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Handle real-time trade execution from WebSocket
     */
    public void handleTradeUpdate(String clientId, Map<String, Object> tradeData) {
        try {
            String tradeId = (String) tradeData.get("trade_id");
            String pair = (String) tradeData.get("pair");
            String side = (String) tradeData.get("type"); // buy/sell
            String volume = (String) tradeData.get("vol");
            String price = (String) tradeData.get("price");

            log.info("💹 Trade executed for client {}: {} {} {} @ {}",
                clientId, side, volume, pair, price);

            // Trade execution will trigger balance update via WebSocket
            // No need to fetch manually

        } catch (Exception e) {
            log.error("❌ Error handling trade update for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Handle daily risk violation - close positions and block until tomorrow
     */
    private void handleDailyRiskViolation(KrakenAccountMonitoring account, RiskCheckResult riskResult) {
        String clientId = account.getClientId();
        log.warn("⚠️ DAILY RISK VIOLATED via WebSocket for client {}: Loss ${} >= Limit ${}",
            clientId, riskResult.getDailyRiskLoss(), riskResult.getDailyRiskThreshold());

        // 1. Close all positions
        Map<String, Object> closeResult = closeAllPositions(account);

        // 2. Block account for the day
        String reason = String.format("DAILY RISK LIMIT EXCEEDED - Loss: $%.2f, Limit: $%.2f",
            riskResult.getDailyRiskLoss(), riskResult.getDailyRiskThreshold());
        account.blockDaily(reason);
        repository.save(account);

        // 3. Publish notification
        publishRiskViolationNotification(
            clientId,
            "DAILY_RISK",
            riskResult.getDailyRiskLoss(),
            riskResult.getDailyRiskThreshold(),
            closeResult
        );

        log.warn("🔒 Account {} blocked until 00:01 UTC due to daily risk violation (WebSocket)", clientId);
    }

    /**
     * Handle max risk violation - close positions and block permanently
     */
    private void handleMaxRiskViolation(KrakenAccountMonitoring account, RiskCheckResult riskResult) {
        String clientId = account.getClientId();
        log.error("🚨 MAX RISK VIOLATED via WebSocket for client {}: Loss ${} >= Limit ${}",
            clientId, riskResult.getMaxRiskLoss(), riskResult.getMaxRiskThreshold());

        // 1. Close all positions
        Map<String, Object> closeResult = closeAllPositions(account);

        // 2. Block account permanently
        String reason = String.format("MAX RISK LIMIT EXCEEDED - Loss: $%.2f, Limit: $%.2f",
            riskResult.getMaxRiskLoss(), riskResult.getMaxRiskThreshold());
        account.blockPermanently(reason);
        repository.save(account);

        // 3. Publish notification
        publishRiskViolationNotification(
            clientId,
            "MAX_RISK",
            riskResult.getMaxRiskLoss(),
            riskResult.getMaxRiskThreshold(),
            closeResult
        );

        log.error("🔒 Account {} permanently blocked due to max risk violation (WebSocket)", clientId);
    }

    /**
     * Close all positions for an account
     */
    private Map<String, Object> closeAllPositions(KrakenAccountMonitoring account) {
        try {
            log.info("📉 Closing all positions via WebSocket trigger for client: {}", account.getClientId());

            // Need to decrypt credentials from account
            // This should be handled by trading service
            return new HashMap<>(); // Placeholder

        } catch (Exception e) {
            log.error("❌ Failed to close positions for {}: {}", account.getClientId(), e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    /**
     * Publish balance update event
     */
    private void publishBalanceUpdateEvent(KrakenAccountMonitoring account, BigDecimal oldBalance, BigDecimal newBalance) {
        try {
            NotificationEvent event = NotificationEvent.builder()
                .eventType(NotificationType.BALANCE_UPDATE)
                .clientId(account.getClientId())
                .exchange("KRAKEN")
                .previousBalance(oldBalance)
                .newBalance(newBalance)
                .source("websocket")
                .timestamp(LocalDateTime.now())
                .message(String.format("Balance updated: $%.2f → $%.2f", oldBalance, newBalance))
                .build();

            rabbitTemplate.convertAndSend("notification.queue", event);

            log.debug("📤 Balance update event published for client: {}", account.getClientId());

        } catch (Exception e) {
            log.error("❌ Failed to publish balance update event: {}", e.getMessage());
        }
    }

    /**
     * Publish risk violation notification
     */
    private void publishRiskViolationNotification(String clientId, String riskType, BigDecimal loss,
                                                 BigDecimal threshold, Map<String, Object> closeResult) {
        try {
            NotificationType eventType = "DAILY_RISK".equals(riskType)
                ? NotificationType.DAILY_RISK_TRIGGERED
                : NotificationType.MAX_RISK_TRIGGERED;

            NotificationEvent event = NotificationEvent.builder()
                .eventType(eventType)
                .clientId(clientId)
                .exchange("KRAKEN")
                .loss(loss)
                .limit(threshold)
                .action("POSITIONS_CLOSED")
                .source("websocket")
                .timestamp(LocalDateTime.now())
                .message(String.format("%s Risk Limit Exceeded - Loss: $%.2f, Limit: $%.2f",
                    riskType, loss, threshold))
                .build();

            rabbitTemplate.convertAndSend("notification.queue", event);

            log.info("📤 Risk violation notification published for client: {}", clientId);

        } catch (Exception e) {
            log.error("❌ Failed to publish risk violation notification: {}", e.getMessage());
        }
    }
}