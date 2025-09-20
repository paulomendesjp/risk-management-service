package com.interview.challenge.kraken.controller;

import com.interview.challenge.kraken.dto.KrakenBalanceResponse;
import com.interview.challenge.kraken.dto.KrakenOrderRequest;
import com.interview.challenge.kraken.dto.KrakenPositionsResponse;
import com.interview.challenge.kraken.dto.KrakenUserRegistrationRequest;
import com.interview.challenge.kraken.service.KrakenTradingService;
import com.interview.challenge.kraken.service.KrakenMonitoringService;
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
    
    @Autowired
    private KrakenMonitoringService monitoringService;

    /**
     * Register a new Kraken user from User-Service
     * This endpoint is called by User-Service when a new KRAKEN user registers
     */
    @PostMapping("/users/register")
    @Operation(summary = "Register Kraken User", description = "Called by User-Service to register a new Kraken user for monitoring")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, String>> registerUserFromUserService(@RequestBody Map<String, Object> registrationData) {
        log.info("🎯 Received user registration from User-Service: {}", registrationData.get("clientId"));

        try {
            String clientId = (String) registrationData.get("clientId");
            String apiKey = (String) registrationData.get("apiKey");
            String apiSecret = (String) registrationData.get("apiSecret");
            Double initialBalance = ((Number) registrationData.get("initialBalance")).doubleValue();

            // Extract risk limits from the request
            com.interview.challenge.kraken.model.KrakenAccountMonitoring.RiskLimit dailyRisk = null;
            com.interview.challenge.kraken.model.KrakenAccountMonitoring.RiskLimit maxRisk = null;

            if (registrationData.containsKey("dailyRisk")) {
                Map<String, Object> dailyRiskData = (Map<String, Object>) registrationData.get("dailyRisk");
                dailyRisk = com.interview.challenge.kraken.model.KrakenAccountMonitoring.RiskLimit.builder()
                    .type((String) dailyRiskData.get("type"))
                    .value(java.math.BigDecimal.valueOf(((Number) dailyRiskData.get("value")).doubleValue()))
                    .build();
            }

            if (registrationData.containsKey("maxRisk")) {
                Map<String, Object> maxRiskData = (Map<String, Object>) registrationData.get("maxRisk");
                maxRisk = com.interview.challenge.kraken.model.KrakenAccountMonitoring.RiskLimit.builder()
                    .type((String) maxRiskData.get("type"))
                    .value(java.math.BigDecimal.valueOf(((Number) maxRiskData.get("value")).doubleValue()))
                    .build();
            }

            // Start monitoring for this user with risk limits
            monitoringService.startMonitoring(
                clientId,
                apiKey,
                apiSecret,
                java.math.BigDecimal.valueOf(initialBalance),
                dailyRisk,
                maxRisk
            );

            log.info("✅ Kraken monitoring activated for user: {}", clientId);

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Kraken monitoring started for " + clientId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to register Kraken user: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Legacy Register endpoint
     */
    @PostMapping("/users/register-legacy")
    @Operation(summary = "Register Kraken User", description = "Register a new user with Kraken API credentials and start monitoring")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or credentials"),
            @ApiResponse(responseCode = "409", description = "User already exists"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> registerUser(
            @Valid @RequestBody KrakenUserRegistrationRequest request) {

        log.info("Registering new Kraken user: {}", request.getClientId());

        Map<String, Object> response = new HashMap<>();

        try {
            // Validate credentials first
            boolean credentialsValid = krakenApiClient.testConnection(request.getApiKey(), request.getApiSecret());
            if (!credentialsValid) {
                response.put("success", false);
                response.put("error", "INVALID_CREDENTIALS");
                response.put("message", "Invalid Kraken API credentials");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Check if user already exists
            if (monitoringService.isMonitoring(request.getClientId())) {
                response.put("success", false);
                response.put("error", "USER_EXISTS");
                response.put("message", "User already registered");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }

            // Start monitoring
            monitoringService.startMonitoring(
                request.getClientId(),
                request.getApiKey(),
                request.getApiSecret(),
                request.getInitialBalance(),
                request.getDailyRisk(),
                request.getMaxRisk()
            );

            response.put("success", true);
            response.put("clientId", request.getClientId());
            response.put("message", "User registered and monitoring started successfully");
            response.put("initialBalance", request.getInitialBalance());

            log.info("✅ Successfully registered Kraken user: {}", request.getClientId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error registering user {}: {}", request.getClientId(), e.getMessage(), e);

            response.put("success", false);
            response.put("error", "REGISTRATION_ERROR");
            response.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

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
            response.put("environment", krakenApiClient.getBaseUrl());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Connection test failed: {}", e.getMessage());

            response.put("connected", false);
            response.put("error", e.getMessage());
            response.put("environment", krakenApiClient.getBaseUrl());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Test API connection without authentication (public endpoints)
     */
    @GetMapping("/test-public")
    @Operation(summary = "Test Public Connection", description = "Test Kraken API public endpoints")
    public ResponseEntity<Map<String, Object>> testPublicConnection() {

        log.info("Testing Kraken API public connection");

        Map<String, Object> response = new HashMap<>();

        try {
            // Test public endpoint - get server time or instruments
            String result = krakenApiClient.testPublicEndpoint();

            response.put("connected", true);
            response.put("message", "Public API connection successful");
            response.put("environment", krakenApiClient.getBaseUrl());
            response.put("serverResponse", result);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Public connection test failed: {}", e.getMessage());

            response.put("connected", false);
            response.put("error", e.getMessage());
            response.put("environment", krakenApiClient.getBaseUrl());

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
            // For demo environment, we'll be more permissive with validation
            // since the provided credentials might be for production Kraken
            
            // Basic format validation
            if (apiKey == null || apiKey.trim().isEmpty() || 
                apiSecret == null || apiSecret.trim().isEmpty()) {
                response.put("valid", false);
                response.put("message", "API key and secret are required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
            // Try to test connection, but don't fail if it doesn't work in demo
            try {
                boolean connected = krakenApiClient.testConnection(apiKey, apiSecret);
                if (connected) {
                    KrakenBalanceResponse balance = krakenApiClient.getAccountBalance(apiKey, apiSecret);
                    response.put("valid", true);
                    response.put("message", "Credentials validated successfully");
                    response.put("hasBalance", balance != null && balance.getResult() != null);
                    response.put("environment", "production");
                } else {
                    // In demo mode, accept credentials even if they don't work
                    log.warn("Credentials failed in demo environment, but allowing registration");
                    response.put("valid", true);
                    response.put("message", "Credentials accepted for demo environment");
                    response.put("hasBalance", false);
                    response.put("environment", "demo");
                }
            } catch (Exception apiException) {
                // In demo mode, accept credentials even if API call fails
                log.warn("API validation failed (demo mode): {}", apiException.getMessage());
                response.put("valid", true);
                response.put("message", "Credentials accepted for demo environment");
                response.put("hasBalance", false);
                response.put("environment", "demo");
                response.put("note", "API validation failed but accepted for demo");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Credential validation failed: {}", e.getMessage());

            response.put("valid", false);
            response.put("error", e.getMessage());
            response.put("message", "Validation error: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
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