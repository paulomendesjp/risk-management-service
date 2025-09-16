package com.interview.challenge.shared.exception;

/**
 * Base exception for trading operations
 */
public class TradingException extends RuntimeException {

    private final String clientId;
    private final String operationType;

    public TradingException(String message, String clientId, String operationType) {
        super(message);
        this.clientId = clientId;
        this.operationType = operationType;
    }

    public TradingException(String message, Throwable cause, String clientId, String operationType) {
        super(message, cause);
        this.clientId = clientId;
        this.operationType = operationType;
    }

    public String getClientId() {
        return clientId;
    }

    public String getOperationType() {
        return operationType;
    }

    /**
     * Specific exception for order placement failures
     */
    public static class OrderPlacementException extends TradingException {
        public OrderPlacementException(String message, String clientId) {
            super(message, clientId, "ORDER_PLACEMENT");
        }
    }

    /**
     * Specific exception for position closure failures
     */
    public static class PositionCloseException extends TradingException {
        public PositionCloseException(String message, String clientId) {
            super(message, clientId, "POSITION_CLOSE");
        }
    }

    /**
     * Specific exception for risk violations
     */
    public static class RiskViolationException extends TradingException {
        private final String riskType;

        public RiskViolationException(String message, String clientId, String riskType) {
            super(message, clientId, "RISK_VIOLATION");
            this.riskType = riskType;
        }

        public String getRiskType() {
            return riskType;
        }
    }
}