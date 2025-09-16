package com.interview.challenge.shared.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 📋 MANDATORY AUDIT LOGGER
 *
 * This class is responsible for all mandatory logging requirements
 * as specified in the system requirements.
 *
 * ALL risk violations MUST be logged through this component
 * to ensure compliance with audit requirements.
 */
@Component
public class MandatoryAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("RISK.AUDIT");
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final ObjectMapper objectMapper;

    public MandatoryAuditLogger() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Log when daily risk limit is triggered (MANDATORY)
     */
    public void logDailyRiskTriggered(String clientId, BigDecimal currentLoss,
                                      BigDecimal dailyLimit, String action) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("event", "DAILY_RISK_TRIGGERED");
        logData.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
        logData.put("clientId", clientId);
        logData.put("currentDailyLoss", currentLoss);
        logData.put("dailyRiskLimit", dailyLimit);
        logData.put("violationPercentage", calculatePercentage(currentLoss, dailyLimit));
        logData.put("action", action);
        logData.put("severity", "HIGH");

        // Set MDC for structured logging
        MDC.put("clientId", clientId);
        MDC.put("riskType", "DAILY_RISK");

        log.error("🚨 DAILY RISK LIMIT TRIGGERED: {}", formatLogData(logData));

        // Clear MDC
        MDC.clear();
    }

    /**
     * Log when max risk limit is triggered (MANDATORY)
     */
    public void logMaxRiskTriggered(String clientId, BigDecimal currentLoss,
                                    BigDecimal maxLimit, String action) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("event", "MAX_RISK_TRIGGERED");
        logData.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
        logData.put("clientId", clientId);
        logData.put("currentTotalLoss", currentLoss);
        logData.put("maxRiskLimit", maxLimit);
        logData.put("violationPercentage", calculatePercentage(currentLoss, maxLimit));
        logData.put("action", action);
        logData.put("severity", "CRITICAL");

        MDC.put("clientId", clientId);
        MDC.put("riskType", "MAX_RISK");

        log.error("🚨 MAX RISK LIMIT TRIGGERED: {}", formatLogData(logData));

        MDC.clear();
    }

    /**
     * Log position closure action (MANDATORY when positions are closed)
     */
    public void logPositionClosure(String clientId, String positionId,
                                   String reason, boolean success) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("event", "POSITION_CLOSURE");
        logData.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
        logData.put("clientId", clientId);
        logData.put("positionId", positionId);
        logData.put("reason", reason);
        logData.put("success", success);
        logData.put("severity", success ? "INFO" : "ERROR");

        MDC.put("clientId", clientId);
        MDC.put("positionId", positionId);

        if (success) {
            log.info("✅ POSITION CLOSED: {}", formatLogData(logData));
        } else {
            log.error("❌ POSITION CLOSURE FAILED: {}", formatLogData(logData));
        }

        MDC.clear();
    }

    /**
     * Log risk check performed (for audit trail)
     */
    public void logRiskCheck(String clientId, BigDecimal currentPnL,
                             BigDecimal dailyLimit, BigDecimal maxLimit) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("event", "RISK_CHECK");
        logData.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
        logData.put("clientId", clientId);
        logData.put("currentPnL", currentPnL);
        logData.put("dailyLimit", dailyLimit);
        logData.put("maxLimit", maxLimit);
        logData.put("dailyUtilization", calculatePercentage(currentPnL, dailyLimit));
        logData.put("maxUtilization", calculatePercentage(currentPnL, maxLimit));

        MDC.put("clientId", clientId);

        log.debug("📊 RISK CHECK: {}", formatLogData(logData));

        MDC.clear();
    }

    private BigDecimal calculatePercentage(BigDecimal value, BigDecimal limit) {
        if (limit == null || limit.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return value.abs()
            .multiply(BigDecimal.valueOf(100))
            .divide(limit.abs(), 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Log system event (for general system events)
     */
    public void logSystemEvent(String eventType, String message) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("event", eventType);
        logData.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
        logData.put("message", message);
        logData.put("severity", "INFO");

        log.info("📋 SYSTEM EVENT: {}", formatLogData(logData));
    }

    /**
     * Log monitoring error
     */
    public void logMonitoringError(String clientId, String error) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("event", "MONITORING_ERROR");
        logData.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
        logData.put("clientId", clientId);
        logData.put("error", error);
        logData.put("severity", "ERROR");

        MDC.put("clientId", clientId);
        log.error("❌ MONITORING ERROR: {}", formatLogData(logData));
        MDC.clear();
    }

    /**
     * Log when max risk limit is triggered (overloaded version with 3 params)
     */
    public void logMaxRiskTriggered(String clientId, BigDecimal currentLoss, BigDecimal maxLimit) {
        logMaxRiskTriggered(clientId, currentLoss, maxLimit, "CLOSE_ALL_POSITIONS");
    }

    /**
     * Log when daily risk limit is triggered (overloaded version with 3 params)
     */
    public void logDailyRiskTriggered(String clientId, BigDecimal dailyLoss, BigDecimal dailyLimit) {
        logDailyRiskTriggered(clientId, dailyLoss, dailyLimit, "CLOSE_ALL_POSITIONS");
    }

    /**
     * Log monitoring started
     */
    public void logMonitoringStarted(String clientId, BigDecimal initialBalance) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("event", "MONITORING_STARTED");
        logData.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
        logData.put("clientId", clientId);
        logData.put("initialBalance", initialBalance);
        logData.put("severity", "INFO");

        MDC.put("clientId", clientId);
        log.info("🚀 MONITORING STARTED: {}", formatLogData(logData));
        MDC.clear();
    }

    /**
     * Log balance update
     */
    public void logBalanceUpdate(String clientId, BigDecimal previousBalance, BigDecimal newBalance) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("event", "BALANCE_UPDATE");
        logData.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
        logData.put("clientId", clientId);
        logData.put("previousBalance", previousBalance);
        logData.put("newBalance", newBalance);
        logData.put("change", newBalance.subtract(previousBalance));
        logData.put("severity", "INFO");

        MDC.put("clientId", clientId);
        log.info("💰 BALANCE UPDATE: {}", formatLogData(logData));
        MDC.clear();
    }

    /**
     * Log risk violation
     */
    public void logRiskViolation(String clientId, String riskType, BigDecimal threshold) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("event", "RISK_VIOLATION");
        logData.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
        logData.put("clientId", clientId);
        logData.put("riskType", riskType);
        logData.put("threshold", threshold);
        logData.put("severity", "WARNING");

        MDC.put("clientId", clientId);
        MDC.put("riskType", riskType);
        log.warn("⚠️ RISK VIOLATION: {}", formatLogData(logData));
        MDC.clear();
    }

    private String formatLogData(Map<String, Object> logData) {
        try {
            // Pretty print JSON for better readability in logs
            return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(logData);
        } catch (Exception e) {
            // Fallback to simple string if JSON serialization fails
            return logData.toString();
        }
    }
}
