package com.interview.challenge.kraken.controller;

import com.interview.challenge.kraken.dto.KrakenBalanceResponse;
import com.interview.challenge.kraken.dto.KrakenOrderRequest;
import com.interview.challenge.kraken.dto.KrakenPositionsResponse;
import com.interview.challenge.kraken.service.KrakenTradingService;
import com.interview.challenge.kraken.client.KrakenApiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Kraken Trading Controller
 *
 * REST endpoints for Kraken Futures trading operations
 */
@Slf4j
@RestController
@RequestMapping("/api/kraken")
@CrossOrigin(origins = "*")
@Tag(name = "Kraken Trading", description = "Kraken Futures trading operations")
public class KrakenTradingController {

    @Autowired
    private KrakenTradingService tradingService;

    @Autowired
    private KrakenApiClient krakenApiClient;

    /**
     * Process webhook order from TradingView
     */
    @PostMapping("/webhook")
    @Operation(summary = "Process TradingView Webhook", description = "Process trading signal from TradingView webhook")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid order request"),
            @ApiResponse(responseCode = "401", description = "Authentication failed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> processWebhook(
            @Valid @RequestBody KrakenOrderRequest orderRequest,
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-API-Secret") String apiSecret) {

        log.info("Received webhook order for client: {}, symbol: {}, side: {}, qty: {}",
                orderRequest.getClientId(), orderRequest.getSymbol(),
                orderRequest.getSide(), orderRequest.getOrderQty());

        try {
            Map<String, Object> result = tradingService.processWebhookOrder(orderRequest, apiKey, apiSecret);

            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }

        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "PROCESSING_ERROR");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Place a manual order
     */
    @PostMapping("/order")
    @Operation(summary = "Place Order", description = "Place a manual order on Kraken Futures")
    public ResponseEntity<Map<String, Object>> placeOrder(
            @Valid @RequestBody KrakenOrderRequest orderRequest,
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-API-Secret") String apiSecret) {

        log.info("Placing manual order: {}", orderRequest);

        try {
            orderRequest.setSource("manual");
            Map<String, Object> result = tradingService.processWebhookOrder(orderRequest, apiKey, apiSecret);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error placing order: {}", e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get account balance
     */
    @GetMapping("/balance/{clientId}")
    @Operation(summary = "Get Balance", description = "Get account balance from Kraken")
    public ResponseEntity<?> getBalance(
            @PathVariable @Parameter(description = "Client ID") String clientId,
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-API-Secret") String apiSecret) {

        log.info("Getting balance for client: {}", clientId);

        try {
            CompletableFuture<KrakenBalanceResponse> balanceFuture =
                tradingService.getAccountBalance(clientId, apiKey, apiSecret);

            KrakenBalanceResponse balance = balanceFuture.get();
            return ResponseEntity.ok(balance);

        } catch (Exception e) {
            log.error("Error getting balance: {}", e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get open positions
     */
    @GetMapping("/positions")
    @Operation(summary = "Get Positions", description = "Get open positions from Kraken")
    public ResponseEntity<?> getPositions(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-API-Secret") String apiSecret) {

        log.info("Getting open positions");

        try {
            KrakenPositionsResponse positions = krakenApiClient.getOpenPositions(apiKey, apiSecret);
            return ResponseEntity.ok(positions);

        } catch (Exception e) {
            log.error("Error getting positions: {}", e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Close all positions
     */
    @PostMapping("/positions/close-all/{clientId}")
    @Operation(summary = "Close All Positions", description = "Close all open positions for a client")
    public ResponseEntity<Map<String, Object>> closeAllPositions(
            @PathVariable @Parameter(description = "Client ID") String clientId,
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-API-Secret") String apiSecret) {

        log.info("Closing all positions for client: {}", clientId);

        try {
            Map<String, Object> result = tradingService.closeAllPositions(clientId, apiKey, apiSecret);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error closing positions: {}", e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Cancel all orders
     */
    @PostMapping("/orders/cancel-all")
    @Operation(summary = "Cancel All Orders", description = "Cancel all open orders")
    public ResponseEntity<Map<String, Object>> cancelAllOrders(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-API-Secret") String apiSecret,
            @RequestParam(required = false) @Parameter(description = "Symbol filter") String symbol) {

        log.info("Cancelling all orders{}", symbol != null ? " for symbol: " + symbol : "");

        try {
            Map<String, Object> result = krakenApiClient.cancelAllOrders(apiKey, apiSecret, symbol);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error cancelling orders: {}", e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Test API connection
     */
    @GetMapping("/test-connection")
    @Operation(summary = "Test Connection", description = "Test Kraken API connection")
    public ResponseEntity<Map<String, Object>> testConnection(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-API-Secret") String apiSecret) {

        log.info("Testing Kraken API connection");

        Map<String, Object> response = new HashMap<>();

        try {
            boolean connected = krakenApiClient.testConnection(apiKey, apiSecret);

            response.put("connected", connected);
            response.put("message", connected ? "Connection successful" : "Connection failed");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Connection test failed: {}", e.getMessage());

            response.put("connected", false);
            response.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Validate API credentials
     */
    @PostMapping("/validate-credentials")
    @Operation(summary = "Validate Credentials", description = "Validate Kraken API credentials")
    public ResponseEntity<Map<String, Object>> validateCredentials(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-API-Secret") String apiSecret) {

        log.info("Validating Kraken API credentials");

        Map<String, Object> response = new HashMap<>();

        try {
            // Test connection and get balance to validate credentials
            boolean connected = krakenApiClient.testConnection(apiKey, apiSecret);

            if (connected) {
                // Try to get balance as additional validation
                KrakenBalanceResponse balance = krakenApiClient.getAccountBalance(apiKey, apiSecret);
                response.put("valid", true);
                response.put("message", "Credentials validated successfully");
                response.put("hasBalance", balance != null && balance.getResult() != null);
            } else {
                response.put("valid", false);
                response.put("message", "Invalid credentials");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Credential validation failed: {}", e.getMessage());

            response.put("valid", false);
            response.put("error", e.getMessage());
            response.put("message", "Invalid credentials: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health Check", description = "Check service health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "kraken-service");
        health.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(health);
    }
}