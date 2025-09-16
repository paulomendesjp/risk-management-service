package com.interview.challenge.shared.model;

/**
 * Risk Type Enumeration
 *
 * Defines different types of risk limits
 */
public enum RiskType {
    DAILY_LOSS_LIMIT,   // Maximum loss allowed per day
    MAX_DRAWDOWN,       // Maximum total drawdown allowed
    MIN_BALANCE,        // Minimum balance threshold
    POSITION_LIMIT,     // Maximum number of positions
    EXPOSURE_LIMIT      // Maximum exposure limit
}