package com.interview.challenge.notification.handler;

import com.interview.challenge.shared.audit.MandatoryAuditLogger;
import com.interview.challenge.shared.enums.NotificationEventType;
import com.interview.challenge.shared.enums.NotificationPriority;
import com.interview.challenge.shared.event.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.Map;

/**
 * Registry for event log handlers - cleaner approach without switch/case
 */
@Component
public class EventLogHandlerRegistry {

    private static final Logger logger = LoggerFactory.getLogger(EventLogHandlerRegistry.class);

    private final MandatoryAuditLogger mandatoryAuditLogger;
    private final Map<NotificationEventType, EventLogHandler> handlers = new EnumMap<>(NotificationEventType.class);

    public EventLogHandlerRegistry(MandatoryAuditLogger mandatoryAuditLogger) {
        this.mandatoryAuditLogger = mandatoryAuditLogger;
    }

    @PostConstruct
    public void initializeHandlers() {
        // MAX_RISK_TRIGGERED handler
        handlers.put(NotificationEventType.MAX_RISK_TRIGGERED, event -> {
            mandatoryAuditLogger.logMaxRiskTriggered(
                event.getClientId(),
                event.getLoss(),
                event.getLimit()
            );
            logger.error("🚨 CRITICAL: {}", event.getMessage());
        });

        // DAILY_RISK_TRIGGERED handler
        handlers.put(NotificationEventType.DAILY_RISK_TRIGGERED, event -> {
            mandatoryAuditLogger.logDailyRiskTriggered(
                event.getClientId(),
                event.getLoss(),
                event.getLimit()
            );
            logger.warn("⚠️ WARNING: {}", event.getMessage());
        });

        // BALANCE_UPDATE handler
        handlers.put(NotificationEventType.BALANCE_UPDATE, event -> {
            mandatoryAuditLogger.logBalanceUpdate(
                event.getClientId(),
                event.getPreviousBalance(),
                event.getNewBalance()
            );
            logger.info("💰 BALANCE: {}", event.getMessage());
        });

        // MONITORING_ERROR handler
        handlers.put(NotificationEventType.MONITORING_ERROR, event -> {
            mandatoryAuditLogger.logMonitoringError(
                event.getClientId(),
                event.getMessage()
            );
            logger.error("🚨 MONITORING ERROR: {}", event.getMessage());
        });

        // POSITION_CLOSED handler
        handlers.put(NotificationEventType.POSITION_CLOSED, event -> {
            mandatoryAuditLogger.logPositionClosure(
                event.getClientId(),
                "multiple", // positionId - using "multiple" for bulk closures
                event.getMessage(),
                true // success
            );
            logger.info("📊 POSITIONS: {}", event.getMessage());
        });

        // ACCOUNT_BLOCKED handler
        handlers.put(NotificationEventType.ACCOUNT_BLOCKED, event -> {
            String eventType = event.getPriority() == NotificationPriority.CRITICAL
                ? "PERMANENT_BLOCK"
                : "DAILY_BLOCK";
            mandatoryAuditLogger.logSystemEvent(
                eventType,
                String.format("Client %s: %s", event.getClientId(), event.getMessage())
            );
            logger.warn("🔒 ACCOUNT BLOCKED: {}", event.getMessage());
        });

        // SYSTEM_EVENT handler (default)
        handlers.put(NotificationEventType.SYSTEM_EVENT, event -> {
            mandatoryAuditLogger.logSystemEvent(
                event.getEventType().toString(),
                event.getMessage()
            );
            logger.info("🔧 SYSTEM: {}", event.getMessage());
        });
    }

    /**
     * Handle the event logging using the registered handler
     */
    public void handleEvent(NotificationEvent event) {
        EventLogHandler handler = handlers.get(event.getEventType());

        if (handler != null) {
            handler.handle(event);
        } else {
            // Fallback for any unmapped event types
            handleDefaultEvent(event);
        }
    }

    /**
     * Default handler for unmapped event types
     */
    private void handleDefaultEvent(NotificationEvent event) {
        mandatoryAuditLogger.logSystemEvent(
            event.getEventType().toString(),
            event.getMessage()
        );
        logger.info("📋 EVENT: {}", event.getMessage());
    }
}