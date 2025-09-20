package com.interview.challenge.kraken.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.challenge.kraken.dto.*;
import com.interview.challenge.kraken.exception.KrakenApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Kraken Futures API Client
 *
 * Handles communication with Kraken Futures REST API
 */
@Slf4j
@Component
public class KrakenApiClient {

    @Value("${kraken.api.spot-url:https://api.kraken.com}")
    private String spotUrl;

    @Value("${kraken.api.futures-url:https://futures.kraken.com}")
    private String futuresUrl;

    @Value("${kraken.api.demo-url:https://demo-futures.kraken.com}")
    private String demoUrl;

    @Value("${kraken.api.use-demo:false}")
    private boolean useDemo;

    @Value("${kraken.api.api-type:spot}")
    private String apiType;

    @Value("${kraken.api.timeout:30}")
    private int timeout;

    @Autowired
    private KrakenAuthenticator authenticator;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String API_VERSION = "/derivatives/api/v3";
    private static final String SPOT_API_VERSION = "/0";

    /**
     * Get the appropriate base URL (demo or production)
     */
    private String getApiBaseUrl() {
        if (apiType.equalsIgnoreCase("spot")) {
            return spotUrl;
        }
        return useDemo ? demoUrl : futuresUrl;
    }

    /**
     * Get the base URL for external access
     */
    public String getBaseUrl() {
        return getApiBaseUrl();
    }

    /**
     * Get account balance
     */
    public KrakenBalanceResponse getAccountBalance(String apiKey, String apiSecret) {
        if (apiType.equalsIgnoreCase("spot")) {
            return getSpotAccountBalance(apiKey, apiSecret);
        }
        return getFuturesAccountBalance(apiKey, apiSecret);
    }

    /**
     * Get Spot account balance
     */
    private KrakenBalanceResponse getSpotAccountBalance(String apiKey, String apiSecret) {
        String path = "/0/private/Balance";
        String url = spotUrl + path;

        try {
            log.info("Fetching Kraken Spot account balance");

            long nonce = System.currentTimeMillis();
            String postData = "nonce=" + nonce;

            // Generate Spot API signature
            String signature = authenticator.generateSpotSignature(apiSecret, path, nonce, postData);

            // Create headers for Spot API
            HttpHeaders headers = new HttpHeaders();
            headers.set("API-Key", apiKey);
            headers.set("API-Sign", signature);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // Make request
            HttpEntity<String> entity = new HttpEntity<>(postData, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            log.debug("Spot balance response: {}", response.getBody());

            // Parse Spot API response
            Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);

            // Check for errors
            if (responseBody.containsKey("error") && !((List) responseBody.get("error")).isEmpty()) {
                String error = responseBody.get("error").toString();
                log.error("Kraken Spot API error: {}", error);
                throw new KrakenApiException("Failed to get balance: " + error);
            }

            // Extract balance data
            Map<String, String> result = (Map<String, String>) responseBody.get("result");

            // Convert to our balance response format
            KrakenBalanceResponse balanceResponse = new KrakenBalanceResponse();
            BigDecimal totalBalance = BigDecimal.ZERO;

            for (Map.Entry<String, String> entry : result.entrySet()) {
                BigDecimal amount = new BigDecimal(entry.getValue());
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    totalBalance = totalBalance.add(amount);
                    log.debug("Asset {}: {}", entry.getKey(), amount);
                }
            }

            balanceResponse.setTotalBalance(totalBalance);
            balanceResponse.setAvailableBalance(totalBalance);
            balanceResponse.setUnrealizedPnl(BigDecimal.ZERO);
            balanceResponse.setSuccess(true);

            log.info("Successfully fetched Spot balance: Total={}", totalBalance);
            return balanceResponse;

        } catch (Exception e) {
            log.error("Error fetching Kraken Spot balance: {}", e.getMessage(), e);
            throw new KrakenApiException("Failed to fetch Spot account balance", e);
        }
    }

    /**
     * Get Futures account balance
     */
    private KrakenBalanceResponse getFuturesAccountBalance(String apiKey, String apiSecret) {
        String path = API_VERSION + "/accounts";
        String url = getApiBaseUrl() + path;

        try {
            log.info("Fetching Kraken Futures account balance");

            // Generate authentication
            String nonce = authenticator.generateNonce();
            String authent = authenticator.generateAuthent(apiSecret, path, nonce, "");

            // Create headers
            HttpHeaders headers = createHeaders(apiKey, authent, nonce);

            // Make request
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            // Parse response
            KrakenBalanceResponse balanceResponse = objectMapper.readValue(response.getBody(), KrakenBalanceResponse.class);

            // Calculate totals
            balanceResponse.setTotalBalance(balanceResponse.calculateTotalBalance());
            balanceResponse.setAvailableBalance(balanceResponse.calculateAvailableBalance());
            balanceResponse.setUnrealizedPnl(balanceResponse.calculateUnrealizedPnl());

            log.info("Successfully fetched Futures balance: Total={}, Available={}",
                    balanceResponse.getTotalBalance(), balanceResponse.getAvailableBalance());

            return balanceResponse;

        } catch (Exception e) {
            log.error("Error fetching Kraken Futures balance: {}", e.getMessage(), e);
            throw new KrakenApiException("Failed to fetch Futures account balance", e);
        }
    }

    /**
     * Place an order
     */
    public KrakenOrderResponse placeOrder(KrakenOrderRequest orderRequest, String apiKey, String apiSecret) {
        if (apiType.equalsIgnoreCase("spot")) {
            return placeSpotOrder(orderRequest, apiKey, apiSecret);
        }
        return placeFuturesOrder(orderRequest, apiKey, apiSecret);
    }

    /**
     * Place a Spot order
     */
    private KrakenOrderResponse placeSpotOrder(KrakenOrderRequest orderRequest, String apiKey, String apiSecret) {
        String path = "/0/private/AddOrder";
        String url = spotUrl + path;

        try {
            log.info("Placing Kraken Spot order: symbol={}, side={}, qty={}",
                    orderRequest.getSymbol(), orderRequest.getSide(), orderRequest.getOrderQty());

            long nonce = System.currentTimeMillis();

            // Prepare Spot order parameters
            String postData = "nonce=" + nonce +
                    "&pair=" + orderRequest.getSymbol().replace("/", "") +  // ETH/USD -> ETHUSD
                    "&type=" + orderRequest.getSide().toLowerCase() +
                    "&ordertype=market" +
                    "&volume=" + orderRequest.getOrderQty();

            // Generate Spot API signature
            String signature = authenticator.generateSpotSignature(apiSecret, path, nonce, postData);

            // Create headers for Spot API
            HttpHeaders headers = new HttpHeaders();
            headers.set("API-Key", apiKey);
            headers.set("API-Sign", signature);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // Make request
            HttpEntity<String> entity = new HttpEntity<>(postData, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            log.debug("Spot order response: {}", response.getBody());

            // Parse Spot API response
            Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);

            // Check for errors
            if (responseBody.containsKey("error") && !((List<?>) responseBody.get("error")).isEmpty()) {
                String error = responseBody.get("error").toString();
                log.error("Kraken Spot order error: {}", error);

                KrakenOrderResponse orderResponse = new KrakenOrderResponse();
                orderResponse.setSuccess(false);
                orderResponse.setError(error);
                return orderResponse;
            }

            // Extract order info
            Map<String, Object> result = (Map<String, Object>) responseBody.get("result");
            Map<String, List<String>> descr = (Map<String, List<String>>) result.get("descr");
            List<String> txid = (List<String>) result.get("txid");

            KrakenOrderResponse orderResponse = new KrakenOrderResponse();
            orderResponse.setSuccess(true);
            if (txid != null && !txid.isEmpty()) {
                orderResponse.setOrderId(txid.get(0));
            }

            log.info("Spot order placed successfully: orderId={}", orderResponse.getOrderId());
            return orderResponse;

        } catch (Exception e) {
            log.error("Error placing Kraken Spot order: {}", e.getMessage(), e);
            throw new KrakenApiException("Failed to place Spot order", e);
        }
    }

    /**
     * Place a Futures order
     */
    private KrakenOrderResponse placeFuturesOrder(KrakenOrderRequest orderRequest, String apiKey, String apiSecret) {
        String path = API_VERSION + "/sendorder";
        String url = getApiBaseUrl() + path;

        try {
            log.info("Placing Kraken Futures order: symbol={}, side={}, qty={}",
                    orderRequest.getSymbol(), orderRequest.getSide(), orderRequest.getOrderQty());

            // Prepare order parameters
            String postData = orderRequest.toKrakenFormat();

            // Generate authentication
            String nonce = authenticator.generateNonce();
            String authent = authenticator.generateAuthent(apiSecret, path, nonce, postData);

            // Create headers
            HttpHeaders headers = createHeaders(apiKey, authent, nonce);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // Make request
            HttpEntity<String> entity = new HttpEntity<>(postData, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            // Parse response
            KrakenOrderResponse orderResponse = objectMapper.readValue(response.getBody(), KrakenOrderResponse.class);

            if (orderResponse.isSuccess()) {
                log.info("Futures order placed successfully: orderId={}", orderResponse.getOrderId());
            } else {
                log.error("Futures order failed: {}", orderResponse.getError());
            }

            return orderResponse;

        } catch (Exception e) {
            log.error("Error placing Kraken Futures order: {}", e.getMessage(), e);
            throw new KrakenApiException("Failed to place Futures order", e);
        }
    }

    /**
     * Get open positions
     */
    public KrakenPositionsResponse getOpenPositions(String apiKey, String apiSecret) {
        if (apiType.equalsIgnoreCase("spot")) {
            // Spot API doesn't have positions, only open orders
            return getSpotOpenOrders(apiKey, apiSecret);
        }
        return getFuturesOpenPositions(apiKey, apiSecret);
    }

    /**
     * Get Spot open orders (Spot doesn't have positions)
     */
    private KrakenPositionsResponse getSpotOpenOrders(String apiKey, String apiSecret) {
        String path = "/0/private/OpenOrders";
        String url = spotUrl + path;

        try {
            log.info("Fetching Kraken Spot open orders");

            long nonce = System.currentTimeMillis();
            String postData = "nonce=" + nonce;

            // Generate Spot API signature
            String signature = authenticator.generateSpotSignature(apiSecret, path, nonce, postData);

            // Create headers for Spot API
            HttpHeaders headers = new HttpHeaders();
            headers.set("API-Key", apiKey);
            headers.set("API-Sign", signature);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // Make request
            HttpEntity<String> entity = new HttpEntity<>(postData, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            log.debug("Spot open orders response: {}", response.getBody());

            // Parse Spot API response
            Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), Map.class);

            // Return empty positions response for Spot (no positions in Spot trading)
            KrakenPositionsResponse positionsResponse = new KrakenPositionsResponse();
            positionsResponse.setResult("success");
            positionsResponse.setOpenPositions(new java.util.ArrayList<>());

            log.info("Spot trading has no positions, only orders");
            return positionsResponse;

        } catch (Exception e) {
            log.error("Error fetching Kraken Spot orders: {}", e.getMessage(), e);
            throw new KrakenApiException("Failed to fetch Spot open orders", e);
        }
    }

    /**
     * Get Futures open positions
     */
    private KrakenPositionsResponse getFuturesOpenPositions(String apiKey, String apiSecret) {
        String path = API_VERSION + "/openpositions";
        String url = getApiBaseUrl() + path;

        try {
            log.info("Fetching Kraken Futures open positions");

            // Generate authentication
            String nonce = authenticator.generateNonce();
            String authent = authenticator.generateAuthent(apiSecret, path, nonce, "");

            // Create headers
            HttpHeaders headers = createHeaders(apiKey, authent, nonce);

            // Make request
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            // Parse response
            KrakenPositionsResponse positionsResponse = objectMapper.readValue(response.getBody(), KrakenPositionsResponse.class);

            log.info("Found {} Futures open positions",
                    positionsResponse.getOpenPositions() != null ? positionsResponse.getOpenPositions().size() : 0);

            return positionsResponse;

        } catch (Exception e) {
            log.error("Error fetching Kraken Futures positions: {}", e.getMessage(), e);
            throw new KrakenApiException("Failed to fetch Futures open positions", e);
        }
    }

    /**
     * Cancel all orders
     */
    public Map<String, Object> cancelAllOrders(String apiKey, String apiSecret, String symbol) {
        if (apiType.equalsIgnoreCase("spot")) {
            return cancelAllSpotOrders(apiKey, apiSecret);
        }
        return cancelAllFuturesOrders(apiKey, apiSecret, symbol);
    }

    /**
     * Cancel all Spot orders
     */
    private Map<String, Object> cancelAllSpotOrders(String apiKey, String apiSecret) {
        String path = "/0/private/CancelAll";
        String url = spotUrl + path;

        try {
            log.info("Cancelling all Kraken Spot orders");

            long nonce = System.currentTimeMillis();
            String postData = "nonce=" + nonce;

            // Generate Spot API signature
            String signature = authenticator.generateSpotSignature(apiSecret, path, nonce, postData);

            // Create headers for Spot API
            HttpHeaders headers = new HttpHeaders();
            headers.set("API-Key", apiKey);
            headers.set("API-Sign", signature);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // Make request
            HttpEntity<String> entity = new HttpEntity<>(postData, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            // Parse response
            Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);

            log.info("Cancel all Spot orders result: {}", result);
            return result;

        } catch (Exception e) {
            log.error("Error cancelling Kraken Spot orders: {}", e.getMessage(), e);
            throw new KrakenApiException("Failed to cancel Spot orders", e);
        }
    }

    /**
     * Cancel all Futures orders
     */
    private Map<String, Object> cancelAllFuturesOrders(String apiKey, String apiSecret, String symbol) {
        String path = API_VERSION + "/cancelallorders";
        String url = getApiBaseUrl() + path;

        try {
            log.info("Cancelling all Kraken Futures orders for symbol: {}", symbol);

            // Prepare parameters
            String postData = symbol != null ? "symbol=" + symbol : "";

            // Generate authentication
            String nonce = authenticator.generateNonce();
            String authent = authenticator.generateAuthent(apiSecret, path, nonce, postData);

            // Create headers
            HttpHeaders headers = createHeaders(apiKey, authent, nonce);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // Make request
            HttpEntity<String> entity = new HttpEntity<>(postData, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            // Parse response
            Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);

            log.info("Cancel all Futures orders result: {}", result);
            return result;

        } catch (Exception e) {
            log.error("Error cancelling Kraken Futures orders: {}", e.getMessage(), e);
            throw new KrakenApiException("Failed to cancel Futures orders", e);
        }
    }

    /**
     * Close all positions
     */
    public Map<String, Object> closeAllPositions(String apiKey, String apiSecret) {
        try {
            log.info("Closing all Kraken positions");

            // First get all open positions
            KrakenPositionsResponse positions = getOpenPositions(apiKey, apiSecret);

            Map<String, Object> results = new HashMap<>();
            int closedCount = 0;

            if (positions.getOpenPositions() != null && !positions.getOpenPositions().isEmpty()) {
                for (KrakenPositionsResponse.Position position : positions.getOpenPositions()) {
                    try {
                        // Create a closing order (opposite side)
                        KrakenOrderRequest closeOrder = KrakenOrderRequest.builder()
                                .symbol(position.getSymbol())
                                .side(position.getSide().equalsIgnoreCase("long") ? "sell" : "buy")
                                .orderQty(position.getSize())
                                .orderType("mkt")
                                .reduceOnly(true)
                                .build();

                        KrakenOrderResponse closeResponse = placeOrder(closeOrder, apiKey, apiSecret);

                        if (closeResponse.isSuccess()) {
                            closedCount++;
                            results.put(position.getSymbol(), "closed");
                        } else {
                            results.put(position.getSymbol(), "failed: " + closeResponse.getError());
                        }

                    } catch (Exception e) {
                        log.error("Error closing position {}: {}", position.getSymbol(), e.getMessage());
                        results.put(position.getSymbol(), "error: " + e.getMessage());
                    }
                }
            }

            results.put("totalClosed", closedCount);
            results.put("totalPositions", positions.getOpenPositions() != null ? positions.getOpenPositions().size() : 0);

            log.info("Closed {} positions", closedCount);

            return results;

        } catch (Exception e) {
            log.error("Error closing all positions: {}", e.getMessage(), e);
            throw new KrakenApiException("Failed to close all positions", e);
        }
    }

    /**
     * Create stop loss order
     */
    public KrakenOrderResponse createStopLossOrder(String symbol, BigDecimal size, BigDecimal stopPrice,
                                                  String apiKey, String apiSecret) {
        try {
            log.info("Creating stop loss order: symbol={}, size={}, stopPrice={}", symbol, size, stopPrice);

            KrakenOrderRequest stopOrder = KrakenOrderRequest.builder()
                    .symbol(symbol)
                    .side("sell")  // Assuming stop loss for long position
                    .orderQty(size)
                    .orderType("stp")  // Stop market order
                    .stopPrice(stopPrice)
                    .reduceOnly(true)
                    .build();

            return placeOrder(stopOrder, apiKey, apiSecret);

        } catch (Exception e) {
            log.error("Error creating stop loss order: {}", e.getMessage(), e);
            throw new KrakenApiException("Failed to create stop loss order", e);
        }
    }

    /**
     * Create HTTP headers for Kraken API
     */
    private HttpHeaders createHeaders(String apiKey, String authent, String nonce) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("APIKey", apiKey);
        headers.set("Authent", authent);
        headers.set("Nonce", nonce);
        headers.set("User-Agent", "KrakenService/1.0");
        return headers;
    }

    /**
     * Test API connection
     */
    public boolean testConnection(String apiKey, String apiSecret) {
        try {
            KrakenBalanceResponse balance = getAccountBalance(apiKey, apiSecret);
            return balance != null && balance.isSuccess();
        } catch (Exception e) {
            log.error("Connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Test public endpoint (no authentication required)
     */
    public String testPublicEndpoint() {
        String path;
        String url;

        if (apiType.equalsIgnoreCase("spot")) {
            // Spot API public endpoint
            path = "/0/public/Time";
            url = spotUrl + path;
        } else {
            // Futures API public endpoint
            path = API_VERSION + "/instruments";
            url = getApiBaseUrl() + path;
        }

        try {
            log.info("Testing Kraken {} public endpoint: {}", apiType, url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "KrakenService/1.0");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            log.info("Public endpoint test successful");
            return response.getBody();

        } catch (Exception e) {
            log.error("Public endpoint test failed: {}", e.getMessage(), e);
            throw new KrakenApiException("Failed to test public endpoint", e);
        }
    }
}
