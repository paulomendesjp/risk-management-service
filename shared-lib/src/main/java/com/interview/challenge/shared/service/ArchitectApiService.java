package com.interview.challenge.shared.service;

import com.interview.challenge.shared.client.ArchitectFeignClient;
import com.interview.challenge.shared.dto.*;
import com.interview.challenge.shared.exception.ArchitectApiException;
import com.interview.challenge.shared.exception.TradingException;
import com.interview.challenge.shared.mapper.ArchitectMapper;
import com.interview.challenge.shared.model.OrderData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Service wrapper for Architect API using FeignClient
 *
 * Usage is MUCH simpler than WebClient:
 *
 * // Set credentials in request headers
 * request.setAttribute("X-API-KEY", apiKey);
 * request.setAttribute("X-API-SECRET", apiSecret);
 *
 * // Call API - HMAC authentication is automatic!
 * Map<String, Object> account = architectApiService.getAccounts();
 *
 * Much cleaner than 200+ lines of WebClient code!
 */
@Service
public class ArchitectApiService {

    private static final Logger logger = LoggerFactory.getLogger(ArchitectApiService.class);

    @Autowired
    private ArchitectFeignClient architectClient;

    @Autowired(required = false)
    private ArchitectMapper architectMapper;

    /**
     * Get account information with automatic HMAC authentication
     * Returns structured DTO - NO MORE MAPS!
     */
    public ArchitectAccountResponse getAccounts(String apiKey, String apiSecret) {
        return executeWithCredentials(apiKey, apiSecret, () -> {
            logger.debug("📊 Getting account info from Architect API");
            List<ArchitectAccountResponse> accounts = architectClient.getAccounts();
            return accounts.isEmpty() ? null : accounts.get(0);
        });
    }

    /**
     * Place trading order with automatic HMAC authentication
     * Accepts OrderData and automatically converts via MapStruct
     */
    public ArchitectOrderResponse placeOrder(OrderData orderData, String apiKey, String apiSecret) {
        return executeWithCredentials(apiKey, apiSecret, () -> {
            logger.info("📈 Placing order on Architect API: {}", orderData.getSymbol());

            // MapStruct automatically converts OrderData to ArchitectOrderRequest!
            if (architectMapper == null) {
                throw new TradingException("ArchitectMapper not available - MapStruct configuration issue",
                        orderData.getClientId(), "PLACE_ORDER");
            }
            ArchitectOrderRequest request = architectMapper.toArchitectOrderRequest(orderData);
            return architectClient.placeOrder(request);
        });
    }

    /**
     * Place order with raw Map (for compatibility)
     */
    public Map<String, Object> placeOrderRaw(Map<String, Object> orderData, String apiKey, String apiSecret) {
        return executeWithCredentials(apiKey, apiSecret, () -> {
            logger.info("📈 Placing raw order on Architect API: {}", orderData.get("symbol"));
            return architectClient.placeOrderRaw(orderData);
        });
    }

    /**
     * Get order details - Returns structured DTO
     */
    public ArchitectOrderResponse getOrder(String orderId, String apiKey, String apiSecret) {
        return executeWithCredentials(apiKey, apiSecret, () -> {
            logger.debug("🔍 Getting order {} from Architect API", orderId);
            return architectClient.getOrder(orderId);
        });
    }

    /**
     * Get open orders - Returns structured DTO list
     */
    public List<ArchitectOrderResponse> getOpenOrders(String apiKey, String apiSecret) {
        return executeWithCredentials(apiKey, apiSecret, () -> {
            logger.debug("📋 Getting open orders from Architect API");
            List<ArchitectOrderResponse> orders = architectClient.getOpenOrders();
            // Bridge service now returns a list directly
            return orders != null ? orders : List.of();
        });
    }

    /**
     * Cancel order - Returns structured DTO
     */
    public ArchitectOrderResponse cancelOrder(String orderId, String apiKey, String apiSecret) {
        return executeWithCredentials(apiKey, apiSecret, () -> {
            logger.info("❌ Cancelling order {} on Architect API", orderId);
            return architectClient.cancelOrder(orderId);
        });
    }

    /**
     * Get positions - Returns structured DTO list
     */
    public List<ArchitectPositionResponse> getPositions(String apiKey, String apiSecret) {
        return executeWithCredentials(apiKey, apiSecret, () -> {
            logger.debug("📊 Getting positions from Architect API");
            return architectClient.getPositions();
        });
    }

    /**
     * Validate credentials and get balance
     */
    public ArchitectBalanceResponse validateCredentialsAndGetBalance(String apiKey, String apiSecret) {
        return getBalance(apiKey, apiSecret);
    }

    /**
     * Get account balance - Returns structured DTO
     */
    public ArchitectBalanceResponse getBalance(String apiKey, String apiSecret) {
        return executeWithCredentials(apiKey, apiSecret, () -> {
            logger.debug("💰 Getting balance from Architect API");
            return architectClient.getBalance();
        });
    }

    /**
     * Get account balance - alias for getBalance for backwards compatibility
     */
    public ArchitectBalanceResponse getAccountBalance(String apiKey, String apiSecret) {
        return getBalance(apiKey, apiSecret);
    }

    /**
     * Get order history - Returns structured DTO list
     */
    public List<ArchitectOrderResponse> getOrderHistory(String apiKey, String apiSecret, int limit) {
        return executeWithCredentials(apiKey, apiSecret, () -> {
            logger.debug("📜 Getting order history from Architect API (limit: {})", limit);
            return architectClient.getOrderHistory(limit);
        });
    }

    /**
     * Test API connectivity and credentials
     */
    public boolean validateCredentials(String apiKey, String apiSecret) {
        try {
            ArchitectAccountResponse account = getAccounts(apiKey, apiSecret);
            boolean isValid = account != null && account.getAccountId() != null;

            if (isValid) {
                logger.info("✅ Architect API credentials validated successfully");
            } else {
                logger.warn("❌ Architect API credentials validation failed - empty response");
            }

            return isValid;

        } catch (ArchitectApiException e) {
            if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
                logger.warn("❌ Architect API credentials are invalid: {}", e.getMessage());
                return false;
            } else {
                logger.error("🚨 Architect API validation error: {}", e.getMessage());
                throw e;
            }

        } catch (Exception e) {
            logger.error("❌ Unexpected error validating Architect API credentials: {}", e.getMessage());
            return false;
        }
    }

    // ========== PRIVATE METHODS ==========

    /**
     * Execute API call with credentials set in request context
     * This allows the ArchitectAuthInterceptor to access them
     */
    private <T> T executeWithCredentials(String apiKey, String apiSecret, CredentialSupplier<T> supplier) {
        // Set credentials in ThreadLocal for async operations
        com.interview.challenge.shared.client.ArchitectAuthInterceptor.CREDENTIALS.set(
            new com.interview.challenge.shared.client.ArchitectAuthInterceptor.ApiCredentials(apiKey, apiSecret)
        );

        // Also set in request attributes for sync operations
        setRequestAttribute("X-API-KEY", apiKey);
        setRequestAttribute("X-API-SECRET", apiSecret);

        try {
            return supplier.get();

        } catch (ArchitectApiException e) {
            logger.error("🚨 Architect API error: {} ({})", e.getMessage(), e.getStatusCode());
            throw e;

        } catch (Exception e) {
            logger.error("❌ Unexpected error calling Architect API: {}", e.getMessage());
            throw new TradingException("Architect API call failed: " + e.getMessage(), e, "unknown", "API_CALL");

        } finally {
            // Clean up credentials from ThreadLocal
            com.interview.challenge.shared.client.ArchitectAuthInterceptor.CREDENTIALS.remove();
            // Clean up credentials from request context
            removeRequestAttribute("X-API-KEY");
            removeRequestAttribute("X-API-SECRET");
        }
    }

    /**
     * Set attribute in current HTTP request
     */
    private void setRequestAttribute(String name, String value) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                request.setAttribute(name, value);
            }
        } catch (Exception e) {
            logger.debug("Could not set request attribute {}: {}", name, e.getMessage());
        }
    }

    /**
     * Remove attribute from current HTTP request
     */
    private void removeRequestAttribute(String name) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                request.removeAttribute(name);
            }
        } catch (Exception e) {
            logger.debug("Could not remove request attribute {}: {}", name, e.getMessage());
        }
    }

    /**
     * Functional interface for API calls with credentials
     */
    @FunctionalInterface
    private interface CredentialSupplier<T> {
        T get() throws Exception;
    }
}




