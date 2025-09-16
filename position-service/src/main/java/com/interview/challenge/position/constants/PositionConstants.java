package com.interview.challenge.position.constants;

/**
 * Constants for Position Service
 */
public final class PositionConstants {

    private PositionConstants() {
        // Private constructor to prevent instantiation
    }

    // Queue and Exchange names
    public static final String RISK_VIOLATIONS_QUEUE = "risk-violations";
    public static final String RISK_EVENTS_EXCHANGE = "risk-events";
    public static final String POSITION_UPDATES_EXCHANGE = "position-updates";
    public static final String NOTIFICATIONS_EXCHANGE = "notifications";

    // Queue TTL Configuration
    public static final long QUEUE_MESSAGE_TTL = 300000L; // 5 minutes in milliseconds
    public static final int MAX_RETRY_ATTEMPTS = 3;
    public static final long RETRY_DELAY_MS = 1000L;

    // API Headers
    public static final String API_KEY_HEADER = "api-key";
    public static final String API_SECRET_HEADER = "api-secret";

    // Logging Messages
    public static final String LOG_POSITION_CLOSURE_START = "Starting position closure for client: {}";
    public static final String LOG_POSITION_CLOSURE_SUCCESS = "Successfully closed {} positions for client: {}";
    public static final String LOG_POSITION_CLOSURE_ERROR = "Error closing positions for client: {}";
    public static final String LOG_RISK_VIOLATION_RECEIVED = "Received risk violation: {} for client: {}";
    public static final String LOG_API_CALL_ERROR = "API call failed for client: {}";
    public static final String LOG_BALANCE_RETRIEVED = "Retrieved balance for client {}: {}";

    // Error Messages
    public static final String ERROR_INVALID_CREDENTIALS = "Invalid API credentials for client: %s";
    public static final String ERROR_POSITION_NOT_FOUND = "Position not found: %s";
    public static final String ERROR_INSUFFICIENT_BALANCE = "Insufficient balance for order";
    public static final String ERROR_ORDER_PLACEMENT_FAILED = "Failed to place order: %s";
    public static final String ERROR_POSITION_CLOSURE_FAILED = "Failed to close position: %s";

    // Business Constants
    public static final double DEFAULT_STOP_LOSS_PERCENTAGE = 0.02; // 2% default stop loss
    public static final int MAX_CONCURRENT_ORDERS = 10;
    public static final long ORDER_TIMEOUT_MS = 30000L; // 30 seconds

    // MDC Keys
    public static final String MDC_CLIENT_ID = "clientId";
    public static final String MDC_OPERATION = "operation";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_SYMBOL = "symbol";
}