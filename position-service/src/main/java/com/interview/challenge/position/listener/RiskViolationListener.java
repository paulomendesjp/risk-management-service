package com.interview.challenge.position.listener;

import com.interview.challenge.position.service.PositionService;
import com.interview.challenge.shared.dto.ClosePositionsResult;
import com.interview.challenge.shared.events.PositionCloseEvent;
import com.interview.challenge.shared.events.RiskViolationEvent;
import com.interview.challenge.shared.events.NotificationEvent;
import com.interview.challenge.shared.model.RiskLimit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * RabbitMQ listener for risk violation events
 * Handles automatic position closure when risk limits are breached
 *
 * Based on requirements:
 * - Daily Risk Trigger: Close all positions and prevent trading until next day
 * - Max Risk Trigger: Close all positions and stop trading permanently
 */
@Component
public class RiskViolationListener {

    private static final Logger logger = LoggerFactory.getLogger(RiskViolationListener.class);

    @Autowired
    private PositionService positionService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${risk.monitoring.api.key:default-key}")
    private String defaultApiKey;

    @Value("${risk.monitoring.api.secret:default-secret}")
    private String defaultApiSecret;

    /**
     * Handle daily risk violation events
     * Requirement: If daily loss ≥ dailyRisk limit, close all positions and block for the day
     */
    @RabbitListener(queues = "position.close.daily.queue")
    public void handleDailyRiskViolation(RiskViolationEvent event) {
        String clientId = event.getClientId();

        MDC.put("clientId", clientId);
        MDC.put("eventType", "DAILY_RISK_VIOLATION");

        try {
            logger.warn("DAILY RISK VIOLATION for client: {} | Current value: {} | Threshold: {}",
                    clientId, event.getCurrentValue(), event.getThreshold());

            // Close all positions immediately
            CompletableFuture<ClosePositionsResult> futureResult = positionService.closeAllPositions(
                    clientId,
                    RiskLimit.RiskType.DAILY_RISK,
                    defaultApiKey,
                    defaultApiSecret
            );

            ClosePositionsResult result = futureResult.get();

            if (result.isSuccess()) {
                logger.info("Successfully closed {} positions for client: {} due to daily risk violation",
                        result.getClosedPositions().size(), clientId);

                // Publish position closed event
                publishPositionClosedEvent(clientId, result, PositionCloseEvent.CloseReason.DAILY_RISK_EXCEEDED);

                // Publish notification
                String message = String.format(
                        "Daily risk limit exceeded. Closed %d positions. Trading blocked until next day.",
                        result.getClosedPositions().size());
                publishNotificationEvent(clientId, "DAILY_RISK_TRIGGERED", message,
                        Map.of("loss", event.getCurrentValue(), "limit", event.getThreshold()));
            } else {
                logger.error("Failed to close positions for client: {} | Error: {}",
                        clientId, result.getErrorMessage());
            }

        } catch (Exception e) {
            logger.error("Error handling daily risk violation for client {}: {}", clientId, e.getMessage(), e);

        } finally {
            MDC.clear();
        }
    }

    /**
     * Handle max risk violation events
     * Requirement: If total loss ≥ maxRisk limit, close all positions and stop trading permanently
     */
    @RabbitListener(queues = "position.close.max.queue")
    public void handleMaxRiskViolation(RiskViolationEvent event) {
        String clientId = event.getClientId();

        MDC.put("clientId", clientId);
        MDC.put("eventType", "MAX_RISK_VIOLATION");

        try {
            logger.error("MAX RISK VIOLATION for client: {} | Current value: {} | Threshold: {}",
                    clientId, event.getCurrentValue(), event.getThreshold());

            // Close all positions immediately
            CompletableFuture<ClosePositionsResult> futureResult = positionService.closeAllPositions(
                    clientId,
                    RiskLimit.RiskType.MAX_RISK,
                    defaultApiKey,
                    defaultApiSecret
            );

            ClosePositionsResult result = futureResult.get();

            if (result.isSuccess()) {
                logger.info("Successfully closed {} positions for client: {} due to max risk violation",
                        result.getClosedPositions().size(), clientId);

                // Publish position closed event
                publishPositionClosedEvent(clientId, result, PositionCloseEvent.CloseReason.MAX_RISK_EXCEEDED);

                // Publish notification
                String message = String.format(
                        "Maximum risk limit exceeded. Closed %d positions. Trading permanently blocked.",
                        result.getClosedPositions().size());
                publishNotificationEvent(clientId, "MAX_RISK_TRIGGERED", message,
                        Map.of("loss", event.getCurrentValue(), "limit", event.getThreshold()));
            } else {
                logger.error("Failed to close positions for client: {} | Error: {}",
                        clientId, result.getErrorMessage());
            }

        } catch (Exception e) {
            logger.error("Error handling max risk violation for client {}: {}", clientId, e.getMessage(), e);

        } finally {
            MDC.clear();
        }
    }

    /**
     * Handle manual position close requests
     */
    @RabbitListener(queues = "position.close.manual.queue")
    public void handleManualPositionClose(PositionCloseEvent event) {
        String clientId = event.getClientId();

        MDC.put("clientId", clientId);
        MDC.put("eventType", "MANUAL_POSITION_CLOSE");

        try {
            logger.info("Manual position close request for client: {} | Reason: {}",
                    clientId, event.getReason());

            // Use provided credentials or defaults
            String apiKey = defaultApiKey;
            String apiSecret = defaultApiSecret;

            // Close all positions
            CompletableFuture<ClosePositionsResult> futureResult = positionService.closeAllPositions(
                    clientId,
                    RiskLimit.RiskType.MAX_RISK, // Treat manual as max risk
                    apiKey,
                    apiSecret
            );

            ClosePositionsResult result = futureResult.get();

            if (result.isSuccess()) {
                logger.info("Successfully closed {} positions for client: {} (manual request)",
                        result.getClosedPositions().size(), clientId);

                // Publish notification
                String message = String.format(
                        "Manual position closure completed. Closed %d positions.",
                        result.getClosedPositions().size());
                publishNotificationEvent(clientId, "MANUAL_CLOSE_COMPLETED", message,
                        Map.of("positions", result.getClosedPositions().size()));
            } else {
                logger.error("Failed to close positions manually for client: {} | Error: {}",
                        clientId, result.getErrorMessage());
            }

        } catch (Exception e) {
            logger.error("Error handling manual position close for client {}: {}", clientId, e.getMessage(), e);

        } finally {
            MDC.clear();
        }
    }

    /**
     * Publish position closed event to other services
     */
    private void publishPositionClosedEvent(String clientId, ClosePositionsResult closeResult,
                                          PositionCloseEvent.CloseReason reason) {
        try {
            PositionCloseEvent event = new PositionCloseEvent(
                    clientId,
                    reason.toString(),
                    closeResult.getClosedPositions().size(),
                    closeResult.getTotalValue(),
                    closeResult.isSuccess()
            );

            rabbitTemplate.convertAndSend(
                    "position.management.exchange",
                    "position.closed",
                    event
            );

            logger.debug("Published position closed event for client: {}", clientId);

        } catch (Exception e) {
            logger.error("Failed to publish position closed event for client: {}", clientId, e);
        }
    }

    /**
     * Publish notification event
     */
    private void publishNotificationEvent(String clientId, String type, String message, Map<String, Object> metadata) {
        try {
            Map<String, Object> enrichedMetadata = new HashMap<>(metadata);
            enrichedMetadata.put("timestamp", LocalDateTime.now().toString());

            NotificationEvent event = new NotificationEvent(
                    clientId,
                    NotificationEvent.NotificationType.valueOf(
                            type.contains("DAILY") ? "DAILY_RISK_TRIGGERED" :
                            type.contains("MAX") ? "MAX_RISK_TRIGGERED" : "SYSTEM_ALERT"),
                    NotificationEvent.Channel.SYSTEM_LOG,
                    "Risk Management Alert",
                    message,
                    LocalDateTime.now(),
                    enrichedMetadata
            );

            rabbitTemplate.convertAndSend(
                    "notification.exchange",
                    "notification.risk",
                    event
            );

            logger.info("Published notification event for client: {} | Type: {}", clientId, type);

        } catch (Exception e) {
            logger.error("Failed to publish notification event for client: {}", clientId, e);
        }
    }
}