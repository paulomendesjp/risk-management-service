package com.interview.challenge.kraken.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Kraken Order Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KrakenOrderResponse {

    private String result;
    private boolean success; // Added for compatibility

    @JsonProperty("sendStatus")
    private SendStatus sendStatus;

    @JsonProperty("orderId")
    private String orderId;

    private String error;

    @JsonProperty("serverTime")
    private String serverTime;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SendStatus {
        private String status; // "placed", "cancelled", "insufficientAvailableFunds"
        @JsonProperty("order_id")
        private String orderId;
        @JsonProperty("receivedTime")
        private String receivedTime;
        private String orderEvents;

        // Manual getters/setters for backup
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }

        public String getReceivedTime() { return receivedTime; }
        public void setReceivedTime(String receivedTime) { this.receivedTime = receivedTime; }

        public String getOrderEvents() { return orderEvents; }
        public void setOrderEvents(String orderEvents) { this.orderEvents = orderEvents; }
    }

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(result) &&
               (sendStatus != null && "placed".equalsIgnoreCase(sendStatus.getStatus()));
    }

    public String getOrderId() {
        if (orderId != null) return orderId;
        if (sendStatus != null) return sendStatus.getOrderId();
        return null;
    }

    // Manual getters/setters for backup
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public SendStatus getSendStatus() { return sendStatus; }
    public void setSendStatus(SendStatus sendStatus) { this.sendStatus = sendStatus; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getServerTime() { return serverTime; }
    public void setServerTime(String serverTime) { this.serverTime = serverTime; }
}