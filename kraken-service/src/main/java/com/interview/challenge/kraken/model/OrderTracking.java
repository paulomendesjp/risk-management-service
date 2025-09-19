package com.interview.challenge.kraken.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order Tracking Model for pyramid and strategy management
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "kraken_order_tracking")
@CompoundIndex(def = "{'clientId': 1, 'strategy': 1, 'status': 1}")
public class OrderTracking {

    @Id
    private String id;

    @Indexed
    private String clientId;

    @Indexed
    private String orderId;

    private String symbol;

    private String side; // buy or sell

    private BigDecimal quantity;

    private BigDecimal price;

    private String orderType;

    @Indexed
    private String strategy;

    @Indexed
    private String status; // ACTIVE, FILLED, CANCELLED, CLOSED

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime filledAt;

    private BigDecimal fillPrice;

    private BigDecimal pnl;

    private Boolean inverse;

    private Boolean pyramid;

    private String parentOrderId; // For related orders (stop loss, take profit)

    private String notes;
}