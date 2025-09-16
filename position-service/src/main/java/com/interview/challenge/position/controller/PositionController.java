package com.interview.challenge.position.controller;

import com.interview.challenge.position.dto.OrderResponse;
import com.interview.challenge.position.service.PositionService;
import com.interview.challenge.shared.dto.*;
import com.interview.challenge.shared.model.OrderData;
import com.interview.challenge.shared.model.RiskLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Position Controller - Real Architect.co Integration
 *
 * All trading operations through real Architect API
 * No simulation - Production-ready endpoints
 */
@RestController
@RequestMapping("/api/positions")
@CrossOrigin(origins = "*")
@Tag(name = "Position Management", description = "Trading position and order management endpoints")
public class PositionController {

    private static final Logger logger = LoggerFactory.getLogger(PositionController.class);

    @Autowired
    private PositionService positionService;

    /**
     * Place a new trading order
     */
    @PostMapping("/orders")
    @Operation(summary = "Place Trading Order", description = "Place a new order via Architect.co API with optional stop-loss")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order placed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid order data"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrderResponse> placeOrder(
            @RequestBody @Parameter(description = "Order details") OrderData orderData,
            @RequestHeader("X-API-KEY") @Parameter(description = "API Key for client") String apiKey,
            @RequestHeader("X-API-SECRET") @Parameter(description = "API Secret for client") String apiSecret) {

        logger.info("Placing order for client: {} | Symbol: {} | Action: {} | Qty: {}",
                orderData.getClientId(), orderData.getSymbol(), orderData.getAction(), orderData.getOrderQty());

        // SYNCHRONOUS - Return immediately
        OrderResponse response = positionService.placeOrder(orderData, apiKey, apiSecret);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Close all positions for a client (called by Risk Monitoring Service)
     */
    @PostMapping("/{clientId}/close-all")
    @Operation(summary = "Close All Positions", description = "Emergency closure of all positions for risk management")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Positions closed successfully"),
            @ApiResponse(responseCode = "206", description = "Partial success - some positions failed to close"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ClosePositionsResult> closeAllPositionsForRisk(
            @PathVariable @Parameter(description = "Client ID") String clientId,
            @RequestParam("riskType") @Parameter(description = "Type of risk violation") RiskLimit.RiskType riskType,
            @RequestHeader("X-API-KEY") String apiKey,
            @RequestHeader("X-API-SECRET") String apiSecret) {

        logger.warn("RISK VIOLATION - Closing all positions for client: {} | Risk Type: {}", clientId, riskType);

        try {
            CompletableFuture<ClosePositionsResult> futureResult = positionService.closeAllPositions(clientId, riskType, apiKey, apiSecret);
            ClosePositionsResult result = futureResult.get();

            if (result.isSuccess()) {
                logger.warn("Position closure completed for client: {} | Closed: {} positions",
                           clientId, result.getClosedPositions());
                return ResponseEntity.ok(result);
            } else if (result.hasFailures()) {
                logger.error("Position closure partially failed for client: {} | Message: {}",
                            clientId, result.getErrorMessage());
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(result);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            }

        } catch (Exception e) {
            logger.error("Error closing positions for client {}: {}", clientId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ClosePositionsResult.error(clientId, riskType.toString(), e.getMessage()));
        }
    }

    /**
     * Get all open positions for a client
     */
    @GetMapping("/{clientId}/positions")
    @Operation(summary = "Get Open Positions", description = "Retrieve all open positions for a client")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Positions retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public CompletableFuture<ResponseEntity<List<ArchitectPositionResponse>>> getOpenPositions(
            @PathVariable @Parameter(description = "Client ID") String clientId,
            @RequestHeader("X-API-KEY") String apiKey,
            @RequestHeader("X-API-SECRET") String apiSecret) {

        logger.debug("Getting open positions for client: {}", clientId);

        return positionService.getOpenPositions(clientId, apiKey, apiSecret)
                .thenApply(positions -> {
                    logger.debug("Retrieved {} open positions for client: {}", positions.size(), clientId);
                    return ResponseEntity.ok(positions);
                })
                .exceptionally(e -> {
                    logger.error("Error getting positions for client {}: {}", clientId, e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
                });
    }

    /**
     * Get open orders
     */
    @GetMapping("/orders")
    @Operation(summary = "Get Open Orders", description = "Retrieve all open orders for the authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public CompletableFuture<ResponseEntity<List<ArchitectOrderResponse>>> getOpenOrders(
            @RequestHeader("X-API-KEY") String apiKey,
            @RequestHeader("X-API-SECRET") String apiSecret) {

        logger.debug("Getting open orders for authenticated user");

        return positionService.getOpenOrders(apiKey, apiSecret)
                .thenApply(orders -> {
                    logger.debug("Retrieved {} open orders", orders.size());
                    return ResponseEntity.ok(orders);
                })
                .exceptionally(e -> {
                    logger.error("Error getting orders: {}", e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
                });
    }

    /**
     * Get account balance for a client
     */
    @GetMapping("/{clientId}/balance")
    @Operation(summary = "Get Account Balance", description = "Retrieve current account balance for a client")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Balance retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getAccountBalance(
            @PathVariable @Parameter(description = "Client ID") String clientId,
            @RequestHeader("X-API-KEY") String apiKey,
            @RequestHeader("X-API-SECRET") String apiSecret) {

        logger.debug("Getting balance for client: {}", clientId);

        return positionService.getAccountBalance(clientId, apiKey, apiSecret)
                .thenApply(balance -> {
                    Map<String, Object> response = Map.of(
                            "clientId", clientId,
                            "balance", balance,
                            "currency", "USD",
                            "timestamp", LocalDateTime.now().toString()
                    );
                    logger.debug("Retrieved balance for client: {} | Balance: {}", clientId, balance);
                    return ResponseEntity.ok(response);
                })
                .exceptionally(e -> {
                    logger.error("Error getting balance for client {}: {}", clientId, e.getMessage());
                    Map<String, Object> errorResult = Map.of(
                            "error", e.getMessage(),
                            "clientId", clientId,
                            "timestamp", LocalDateTime.now().toString()
                    );
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
                });
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health Check", description = "Check service health status")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = Map.of(
            "success", true,
            "service", "Position Service",
            "status", "UP",
            "version", "3.0.0",
            "integration", "Architect.co API",
            "timestamp", LocalDateTime.now().toString()
        );

        return ResponseEntity.ok(response);
    }
}