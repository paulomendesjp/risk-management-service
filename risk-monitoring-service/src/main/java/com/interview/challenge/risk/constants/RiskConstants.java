package com.interview.challenge.risk.constants;

import java.math.BigDecimal;

public final class RiskConstants {

    private RiskConstants() {
        // Prevent instantiation
    }

    // Risk Thresholds
    public static final String WARNING_THRESHOLD_PERCENTAGE = "0.8";
    public static final BigDecimal WARNING_THRESHOLD_MULTIPLIER = new BigDecimal("0.8");
    public static final BigDecimal PERCENTAGE_DIVISOR = new BigDecimal("100");

    // Default Risk Limits
    public static final String DEFAULT_MAX_RISK_PERCENTAGE = "30";
    public static final String DEFAULT_DAILY_RISK_AMOUNT = "5000";

    // URLs
    public static final String PYTHON_BRIDGE_BASE_URL = "http://localhost:8090";
    public static final String START_MONITORING_ENDPOINT = "/start-monitoring/";
    public static final String STOP_MONITORING_ENDPOINT = "/stop-monitoring/";

    // WebSocket Message Types
    public static final String MSG_TYPE_BALANCE_UPDATE = "balance_update";
    public static final String MSG_TYPE_RISK_VIOLATION = "risk_violation";
    public static final String MSG_TYPE_MONITORING_ERROR = "monitoring_error";
    public static final String MSG_TYPE_HEARTBEAT = "heartbeat";
    public static final String MSG_TYPE_HEARTBEAT_RESPONSE = "heartbeat_response";
    public static final String MSG_TYPE_CONNECTION_ESTABLISHED = "connection_established";
    public static final String MSG_TYPE_ERROR = "error";

    // Risk Status Messages
    public static final String MAX_RISK_MESSAGE_FORMAT = "Max risk limit exceeded: Loss %s >= Limit %s";
    public static final String DAILY_RISK_MESSAGE_FORMAT = "Daily risk limit exceeded: Daily Loss %s >= Limit %s";
    public static final String MAX_RISK_PERMANENT_BLOCK_MSG = "MAX RISK LIMIT EXCEEDED - Account permanently blocked";
    public static final String DAILY_RISK_DAILY_BLOCK_MSG = "DAILY RISK LIMIT EXCEEDED - Trading blocked until tomorrow";

    // Event Sources
    public static final String SOURCE_RISK_MONITORING = "risk_monitoring";
    public static final String SOURCE_WEBSOCKET = "websocket";
    public static final String SOURCE_MANUAL_UPDATE = "manual_update";

    // RabbitMQ Exchanges
    public static final String EXCHANGE_RISK_VIOLATIONS = "risk.violations";
    public static final String EXCHANGE_BALANCE_UPDATES = "balance.updates";
    public static final String EXCHANGE_NOTIFICATIONS = "notifications";

    // Risk Violation Reasons
    public static final String VIOLATION_REASON_MAX_RISK = "MAX_RISK_VIOLATION";
    public static final String VIOLATION_REASON_DAILY_RISK = "DAILY_RISK_VIOLATION";

    // HTTP Headers
    public static final String HEADER_API_KEY = "api-key";
    public static final String HEADER_API_SECRET = "api-secret";
    public static final String HEADER_X_API_KEY = "X-API-Key";
    public static final String HEADER_X_API_SECRET = "X-API-Secret";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_TYPE_JSON = "application/json";

    // Response Status
    public static final String STATUS_HEALTHY = "healthy";
    public static final String STATUS_PROCESSED = "processed";
    public static final String STATUS_UPDATED = "updated";
    public static final String STATUS_ERROR_LOGGED = "error_logged";
    public static final String STATUS_STARTED = "started";
    public static final String STATUS_STOPPED = "stopped";
    public static final String STATUS_NOT_RUNNING = "not_running";

    // Event Actions
    public static final String ACTION_CLOSE_ALL_POSITIONS = "CLOSE_ALL_POSITIONS";
    public static final String ACTION_PERMANENT_BLOCK = "All positions closed. Trading permanently disabled.";
    public static final String ACTION_DAILY_BLOCK = "All positions closed. Trading disabled for today.";

    // Cron Expressions
    public static final String DAILY_RESET_CRON = "0 0 9 * * ?";

    // WebSocket Alert Levels
    public static final String ALERT_LEVEL_CRITICAL = "CRITICAL";
    public static final String ALERT_LEVEL_WARNING = "WARNING";
    public static final String ALERT_LEVEL_INFO = "INFO";
    public static final String ALERT_LEVEL_ERROR = "error";
}