package com.interview.challenge.shared.event;

public enum NotificationType {
    RISK_VIOLATION,
    RISK_WARNING,
    MONITORING_ERROR,
    DAILY_RESET,
    POSITION_CLOSED,
    BALANCE_UPDATE,
    ACCOUNT_BLOCKED,
    ORDER_PLACED,
    SYSTEM_EVENT,
    MAX_RISK_TRIGGERED,
    DAILY_RISK_TRIGGERED
}