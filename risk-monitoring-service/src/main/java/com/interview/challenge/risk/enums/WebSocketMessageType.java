package com.interview.challenge.risk.enums;

public enum WebSocketMessageType {
    BALANCE_UPDATE("balance_update"),
    RISK_VIOLATION("risk_violation"),
    RISK_ALERT("risk_alert"),
    RISK_STATUS("risk_status"),
    MONITORING_ERROR("monitoring_error"),
    HEARTBEAT("heartbeat"),
    HEARTBEAT_RESPONSE("heartbeat_response"),
    CONNECTION_ESTABLISHED("connection_established"),
    ERROR("error"),
    SYSTEM_NOTIFICATION("system_notification"),
    DAILY_RESET("daily_reset");

    private final String type;

    WebSocketMessageType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public static WebSocketMessageType fromType(String type) {
        for (WebSocketMessageType messageType : WebSocketMessageType.values()) {
            if (messageType.type.equals(type)) {
                return messageType;
            }
        }
        throw new IllegalArgumentException("Unknown WebSocketMessageType: " + type);
    }
}