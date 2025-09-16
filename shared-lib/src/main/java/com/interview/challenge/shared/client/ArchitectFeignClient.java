package com.interview.challenge.shared.client;

import com.interview.challenge.shared.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Feign Client for Architect.co API
 * 
 * Now uses structured DTOs instead of generic Maps!
 * HMAC authentication handled by ArchitectAuthInterceptor
 * MapStruct handles automatic conversions
 */
@FeignClient(
    name = "architect-bridge",
    url = "${architect.bridge.endpoint:http://localhost:8090}",
    configuration = com.interview.challenge.shared.config.ArchitectFeignConfig.class
)
public interface ArchitectFeignClient {

    /**
     * Get account information - Returns structured DTO
     * GET /accounts
     */
    @GetMapping("/accounts")
    List<ArchitectAccountResponse> getAccounts();

    /**
     * Place a trading order - Accepts structured DTO
     * POST /place-order
     */
    @PostMapping("/place-order")
    ArchitectOrderResponse placeOrder(@RequestBody ArchitectOrderRequest orderRequest);

    /**
     * Get order details - Returns structured DTO
     * GET /get-order-status/{orderId}
     */
    @GetMapping("/get-order-status/{orderId}")
    ArchitectOrderResponse getOrder(@PathVariable("orderId") String orderId);

    /**
     * Get open orders - Returns structured DTO list
     * GET /orders/list
     */
    @GetMapping("/orders/list")
    List<ArchitectOrderResponse> getOpenOrders();

    /**
     * Cancel an order - Returns structured DTO
     * DELETE /orders/{orderId}
     */
    @DeleteMapping("/orders/{orderId}")
    ArchitectOrderResponse cancelOrder(@PathVariable("orderId") String orderId);

    /**
     * Get positions - Returns structured DTO list
     * GET /api/v1/positions
     */
    @GetMapping("/api/v1/positions")
    List<ArchitectPositionResponse> getPositions();

    /**
     * Get account balance - Returns structured DTO
     * GET /accounts/balance
     */
    @GetMapping("/accounts/balance")
    ArchitectBalanceResponse getBalance();

    /**
     * Get trading history - Returns structured DTO list
     * GET /api/v1/orders/history
     */
    @GetMapping("/api/v1/orders/history")
    List<ArchitectOrderResponse> getOrderHistory(@RequestParam(value = "limit", defaultValue = "100") int limit);

    // ========== RAW MAP ENDPOINTS (for compatibility) ==========

    /**
     * Raw map endpoints for cases where structured DTOs don't match API exactly
     */
    @GetMapping("/api/v1/accounts")
    Map<String, Object> getAccountsRaw();

    @PostMapping("/api/v1/orders")
    Map<String, Object> placeOrderRaw(@RequestBody Map<String, Object> orderData);
}





