package com.interview.challenge.user.constants;

public final class UserConstants {

    private UserConstants() {
        // Prevent instantiation
    }

    // Response Keys
    public static final String KEY_SUCCESS = "success";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_ERROR = "error";
    public static final String KEY_CLIENT_ID = "clientId";
    public static final String KEY_INITIAL_BALANCE = "initialBalance";
    public static final String KEY_MAX_RISK = "maxRisk";
    public static final String KEY_DAILY_RISK = "dailyRisk";
    public static final String KEY_CREATED_AT = "createdAt";
    public static final String KEY_UPDATED_AT = "updatedAt";
    public static final String KEY_REASON = "reason";
    public static final String KEY_CAN_TRADE = "canTrade";
    public static final String KEY_DAILY_BLOCKED = "dailyBlocked";
    public static final String KEY_PERMANENT_BLOCKED = "permanentBlocked";
    public static final String KEY_API_KEY = "apiKey";
    public static final String KEY_API_SECRET = "apiSecret";
    public static final String KEY_VALID = "valid";
    public static final String KEY_STATUS = "status";
    public static final String KEY_SERVICE = "service";
    public static final String KEY_TIMESTAMP = "timestamp";

    // Messages
    public static final String MSG_USER_REGISTERED = "User registered successfully";
    public static final String MSG_USER_NOT_FOUND = "User not found";
    public static final String MSG_USER_ALREADY_EXISTS = "User already exists";
    public static final String MSG_RISK_LIMITS_UPDATED = "Risk limits updated successfully";
    public static final String MSG_USER_BLOCKED_DAILY = "User blocked daily";
    public static final String MSG_USER_BLOCKED_PERMANENTLY = "User blocked permanently";
    public static final String MSG_CREDENTIALS_UPDATED = "Credentials updated successfully";
    public static final String MSG_USER_DELETED = "User deleted successfully";
    public static final String MSG_DAILY_BLOCK_RESET = "Daily block reset successfully";
    public static final String MSG_CREDENTIALS_VALID = "Credentials are valid";
    public static final String MSG_CREDENTIALS_INVALID = "Credentials are invalid";
    public static final String MSG_API_KEY_SECRET_REQUIRED = "API key and secret are required";
    public static final String MSG_RISK_LIMITS_REQUIRED = "Both maxRisk and dailyRisk are required";
    public static final String MSG_CANNOT_UNBLOCK_PERMANENT = "Cannot unblock permanently blocked user";

    // Default Reasons
    public static final String DEFAULT_DAILY_BLOCK_REASON = "Daily risk limit exceeded";
    public static final String DEFAULT_PERMANENT_BLOCK_REASON = "Maximum risk limit exceeded";

    // Risk Limit Types
    public static final String RISK_TYPE_PERCENTAGE = "percentage";
    public static final String RISK_TYPE_ABSOLUTE = "absolute";

    // RabbitMQ Exchanges/Queues
    public static final String EXCHANGE_USER_REGISTRATIONS = "user.registrations";
    public static final String EXCHANGE_USER_UPDATES = "user.updates";

    // Service Info
    public static final String SERVICE_NAME = "user-service";
    public static final String SERVICE_STATUS_UP = "UP";

    // Validation
    public static final String VALIDATION_INVALID_RISK_LIMIT = "Invalid risk limit";

    // Log Messages
    public static final String LOG_REGISTERING_USER = "🆕 Registering new user: {}";
    public static final String LOG_VALIDATING_CREDENTIALS = "🔐 Validating API credentials for user: {}";
    public static final String LOG_USER_REGISTERED = "✅ User registered successfully: {} with initial balance: ${}";
    public static final String LOG_REGISTRATION_FAILED = "❌ Failed to register user {}: {}";
    public static final String LOG_USER_DELETED = "User deleted: {}";
    public static final String LOG_DAILY_BLOCK_RESET = "Daily block reset for user: {}";
    public static final String LOG_RISK_LIMITS_UPDATED = "📊 Updated risk limits for user: {}";
    public static final String LOG_USER_BLOCKED_DAILY = "🚫 User blocked for today: {} - Reason: {}";
    public static final String LOG_USER_BLOCKED_PERMANENT = "⛔ User permanently blocked: {} - Reason: {}";
    public static final String LOG_USER_UNBLOCKED = "✅ User unblocked: {}";
    public static final String LOG_CREDENTIALS_UPDATED = "🔐 Updated credentials for user: {}";
    public static final String LOG_CREDENTIALS_VALIDATED = "✅ Credentials validated for user: {} - Balance: ${}";
    public static final String LOG_EVENT_PUBLISHED = "📤 Published {} event for: {}";
    public static final String LOG_EVENT_PUBLISH_FAILED = "Failed to publish {} event: {}";
    public static final String LOG_RABBITMQ_NOT_CONFIGURED = "RabbitMQ not configured - skipping event publication";
}