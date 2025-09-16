package com.interview.challenge.risk.service;

import com.interview.challenge.risk.constants.RiskConstants;
import com.interview.challenge.risk.enums.ViolationType;
import com.interview.challenge.shared.audit.MandatoryAuditLogger;
import com.interview.challenge.shared.event.NotificationEvent;
import com.interview.challenge.shared.event.RiskViolationEvent;
import com.interview.challenge.shared.event.BalanceUpdateEvent;
import com.interview.challenge.shared.model.RiskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class NotificationPublisher {

    private static final Logger logger = LoggerFactory.getLogger(NotificationPublisher.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MandatoryAuditLogger mandatoryAuditLogger;

    /**
     * Publish risk violation event
     */
    public void publishRiskViolationEvent(String clientId, ViolationType violationType,
                                         BigDecimal lossAmount, String reason) {
        try {
            RiskViolationEvent event = new RiskViolationEvent();
            event.setClientId(clientId);
            event.setRiskType(mapViolationToRiskType(violationType));
            event.setThreshold(lossAmount);
            event.setCurrentValue(BigDecimal.ZERO);
            event.setAction(reason);
            event.setTimestamp(LocalDateTime.now());

            rabbitTemplate.convertAndSend(RiskConstants.EXCHANGE_RISK_VIOLATIONS, event);
            logger.info("Published risk violation event for client: {} - Type: {}", clientId, violationType);

        } catch (Exception e) {
            logger.error("Error publishing risk violation event for client {}: {}", clientId, e.getMessage());
            fallbackToDirectLogging(clientId, violationType.getDescription(), e.getMessage());
        }
    }

    /**
     * Publish balance update event
     */
    public void publishBalanceUpdateEvent(String clientId, BigDecimal newBalance,
                                         BigDecimal previousBalance, String source) {
        try {
            BalanceUpdateEvent event = new BalanceUpdateEvent();
            event.setClientId(clientId);
            event.setNewBalance(newBalance);
            event.setPreviousBalance(previousBalance);
            event.setSource(source != null ? source : RiskConstants.SOURCE_RISK_MONITORING);
            event.setTimestamp(LocalDateTime.now());

            rabbitTemplate.convertAndSend(RiskConstants.EXCHANGE_BALANCE_UPDATES, event);
            logger.debug("Published balance update event for client: {}", clientId);

        } catch (Exception e) {
            logger.error("Error publishing balance update event for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Publish notification event
     */
    public void publishNotificationEvent(NotificationEvent notificationEvent) {
        try {
            logger.info("📢 Publishing notification event: {} for client {}",
                notificationEvent.getEventType().toString(), notificationEvent.getClientId());

            rabbitTemplate.convertAndSend(RiskConstants.EXCHANGE_NOTIFICATIONS, notificationEvent);
            logger.debug("✅ Notification event published successfully");

        } catch (Exception e) {
            logger.error("❌ Error publishing notification event for client {}: {}",
                notificationEvent.getClientId(), e.getMessage());
            fallbackToDirectLogging(notificationEvent.getClientId(),
                notificationEvent.getEventType().toString(), e.getMessage());
        }
    }

    /**
     * Publish max risk triggered notification
     */
    public void publishMaxRiskTriggered(String clientId, BigDecimal loss, BigDecimal limit) {
        NotificationEvent event = NotificationEvent.maxRiskTriggered(clientId, loss, limit);
        publishNotificationEvent(event);
    }

    /**
     * Publish daily risk triggered notification
     */
    public void publishDailyRiskTriggered(String clientId, BigDecimal loss, BigDecimal limit) {
        NotificationEvent event = NotificationEvent.dailyRiskTriggered(clientId, loss, limit);
        publishNotificationEvent(event);
    }

    /**
     * Publish monitoring error notification
     */
    public void publishMonitoringError(String clientId, String error) {
        NotificationEvent event = NotificationEvent.monitoringError(clientId, error);
        publishNotificationEvent(event);
    }

    /**
     * Publish balance update notification
     */
    public void publishBalanceUpdate(String clientId, BigDecimal newBalance,
                                    BigDecimal previousBalance, String source) {
        NotificationEvent event = NotificationEvent.balanceUpdate(clientId, newBalance, previousBalance, source);
        publishNotificationEvent(event);
    }

    /**
     * Map violation type to risk type
     */
    private RiskType mapViolationToRiskType(ViolationType violationType) {
        switch (violationType) {
            case MAX_RISK:
                return RiskType.MAX_DRAWDOWN;
            case DAILY_RISK:
                return RiskType.DAILY_LOSS_LIMIT;
            default:
                return RiskType.MAX_DRAWDOWN;
        }
    }

    /**
     * Fallback to direct logging if queue fails
     */
    private void fallbackToDirectLogging(String clientId, String eventType, String error) {
        mandatoryAuditLogger.logSystemEvent("NOTIFICATION_QUEUE_FAILED",
            String.format("Failed to send %s notification for client %s: %s",
                eventType, clientId, error));
    }
}