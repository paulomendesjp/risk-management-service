package com.interview.challenge.user.controller;

import com.interview.challenge.shared.model.ClientConfiguration;
import com.interview.challenge.shared.model.RiskLimit;
import com.interview.challenge.user.constants.UserConstants;
import com.interview.challenge.user.dto.ApiResponse;
import com.interview.challenge.user.dto.UserRegistrationRequest;
import com.interview.challenge.user.enums.ErrorCode;
import com.interview.challenge.user.helper.UserResponseBuilder;
import com.interview.challenge.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * User Management Controller
 * Handles all user registration, configuration, and management operations
 *
 * Endpoints follow the exact requirements from the document:
 * - User registration with API credentials and risk limits
 * - Risk limit updates
 * - User blocking (daily/permanent)
 * - Credential management
 */
@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
@Tag(name = "User Management", description = "Client registration and configuration endpoints")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    /**
     * Register new user with exact document format
     */
    @PostMapping("/register")
    @Operation(summary = "Register New User", description = "Register a new client with API credentials and risk limits")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User registered successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or user already exists"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> registerUser(
            @Valid @RequestBody @Parameter(description = "User registration details") UserRegistrationRequest request) {
        try {
            logger.info("Registering new user: {}", request.getClientId());

            ClientConfiguration user = userService.registerUser(request);
            Map<String, Object> response = UserResponseBuilder.buildRegistrationResponse(user);

            logger.info("User registered successfully: {}", request.getClientId());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.warn("User registration validation error: {}", e.getMessage());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(UserResponseBuilder.buildErrorResponse(
                            ErrorCode.VALIDATION_ERROR.getCode(), e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to register user: {}", e.getMessage(), e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(UserResponseBuilder.buildErrorResponse(
                            ErrorCode.REGISTRATION_FAILED.getCode(),
                            "Failed to register user: " + e.getMessage()));
        }
    }

    /**
     * Get user by client ID
     */
    @GetMapping("/{clientId}")
    @Operation(summary = "Get User", description = "Retrieve user configuration by client ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getUser(
            @PathVariable @Parameter(description = "Client ID") String clientId) {
        try {
            logger.debug("Getting user: {}", clientId);

            Optional<ClientConfiguration> userOpt = userService.getUserByClientId(clientId);

            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(UserResponseBuilder.buildErrorResponse(
                                ErrorCode.USER_NOT_FOUND.getCode(),
                                UserConstants.MSG_USER_NOT_FOUND + ": " + clientId));
            }

            ClientConfiguration user = userOpt.get();
            Map<String, Object> response = UserResponseBuilder.buildUserDetailsResponse(user);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to get user {}: {}", clientId, e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(UserResponseBuilder.buildErrorResponse(
                            ErrorCode.GET_USER_FAILED.getCode(), e.getMessage()));
        }
    }

    /**
     * Update risk limits for a user
     */
    @PutMapping("/{clientId}/risk-limits")
    @Operation(summary = "Update Risk Limits", description = "Update maximum and daily risk limits for a user")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Risk limits updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> updateRiskLimits(
            @PathVariable @Parameter(description = "Client ID") String clientId,
            @Valid @RequestBody @Parameter(description = "New risk limits") Map<String, UserRegistrationRequest.RiskLimitDto> request) {

        try {
            logger.info("Updating risk limits for user: {}", clientId);

            UserRegistrationRequest.RiskLimitDto maxRiskDto = request.get("maxRisk");
            UserRegistrationRequest.RiskLimitDto dailyRiskDto = request.get("dailyRisk");

            if (maxRiskDto == null || dailyRiskDto == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "INVALID_REQUEST");
                errorResponse.put("message", "Both maxRisk and dailyRisk are required");

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }

            // Convert DTOs to entities
            RiskLimit maxRisk = convertRiskLimit(maxRiskDto);
            RiskLimit dailyRisk = convertRiskLimit(dailyRiskDto);

            ClientConfiguration updatedUser = userService.updateRiskLimits(clientId, maxRisk, dailyRisk);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Risk limits updated successfully");
            response.put("clientId", updatedUser.getClientId());
            response.put("maxRisk", updatedUser.getMaxRisk());
            response.put("dailyRisk", updatedUser.getDailyRisk());
            response.put("updatedAt", updatedUser.getUpdatedAt());

            logger.info("Risk limits updated successfully for user: {}", clientId);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Risk limit update validation error for {}: {}", clientId, e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "VALIDATION_ERROR");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);

        } catch (Exception e) {
            logger.error("Failed to update risk limits for {}: {}", clientId, e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "UPDATE_FAILED");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Check if user can trade
     */
    @GetMapping("/{clientId}/can-trade")
    @Operation(summary = "Check Trading Status", description = "Check if a user is allowed to trade")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trading status retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> canUserTrade(
            @PathVariable @Parameter(description = "Client ID") String clientId) {
        try {
            boolean canTrade = userService.canUserTrade(clientId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("clientId", clientId);
            response.put("canTrade", canTrade);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to check trading status for {}: {}", clientId, e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "CHECK_FAILED");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get decrypted API credentials (internal use only)
     */
    @GetMapping("/{clientId}/credentials")
    @Operation(summary = "Get API Credentials", description = "Retrieve decrypted API credentials for internal service use")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Credentials retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, String>> getCredentials(
            @PathVariable @Parameter(description = "Client ID") String clientId) {
        try {
            logger.debug("Getting credentials for user: {}", clientId);

            Map<String, String> credentials = userService.getDecryptedCredentials(clientId);

            // Return just the map with apiKey and apiSecret
            return ResponseEntity.ok(credentials);

        } catch (IllegalArgumentException e) {
            // For error cases, return empty map or null
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new HashMap<>());

        } catch (Exception e) {
            logger.error("Failed to get credentials for {}: {}", clientId, e.getMessage());
            // For error cases, return empty map or null
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new HashMap<>());
        }
    }

    /**
     * Block user daily (called by Risk Monitoring Service)
     */
    @PostMapping("/{clientId}/block-daily")
    @Operation(summary = "Block User Daily", description = "Block a user for the current trading day")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User blocked successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> blockUserDaily(
            @PathVariable @Parameter(description = "Client ID") String clientId,
            @RequestBody @Parameter(description = "Block reason") Map<String, String> request) {
        try {
            String reason = request.getOrDefault("reason", "Daily risk limit exceeded");

            logger.warn("Blocking user daily: {} | Reason: {}", clientId, reason);

            userService.blockUserDaily(clientId, reason);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User blocked daily");
            response.put("clientId", clientId);
            response.put("reason", reason);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to block user daily {}: {}", clientId, e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "BLOCK_FAILED");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Block user permanently (called by Risk Monitoring Service)
     */
    @PostMapping("/{clientId}/block-permanent")
    @Operation(summary = "Block User Permanently", description = "Permanently block a user from trading")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User blocked successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> blockUserPermanently(
            @PathVariable @Parameter(description = "Client ID") String clientId,
            @RequestBody @Parameter(description = "Block reason") Map<String, String> request) {
        try {
            String reason = request.getOrDefault("reason", "Maximum risk limit exceeded");

            logger.error("Blocking user permanently: {} | Reason: {}", clientId, reason);

            userService.blockUserPermanently(clientId, reason);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User blocked permanently");
            response.put("clientId", clientId);
            response.put("reason", reason);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to block user permanently {}: {}", clientId, e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "BLOCK_FAILED");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health Check", description = "Check service health status")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put(UserConstants.KEY_SERVICE, UserConstants.SERVICE_NAME);
        response.put(UserConstants.KEY_STATUS, UserConstants.SERVICE_STATUS_UP);
        response.put(UserConstants.KEY_TIMESTAMP, System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * Get all users (admin endpoint)
     */
    @GetMapping("/all")
    @Operation(summary = "Get All Users", description = "Retrieve all registered users (admin only)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<ClientConfiguration>> getAllUsers() {
        try {
            logger.info("Getting all users");
            List<ClientConfiguration> users = userService.getAllUsers();
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            logger.error("Error getting all users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update user's API credentials
     * PUT /api/users/{clientId}/credentials
     */
    @PutMapping("/{clientId}/credentials")
    public ResponseEntity<Map<String, Object>> updateCredentials(
            @PathVariable String clientId,
            @RequestBody Map<String, String> credentials) {

        Map<String, Object> response = new HashMap<>();

        try {
            String apiKey = credentials.get("apiKey");
            String apiSecret = credentials.get("apiSecret");

            if (apiKey == null || apiSecret == null) {
                response.put("success", false);
                response.put("message", "API key and secret are required");
                return ResponseEntity.badRequest().body(response);
            }

            logger.info("Updating credentials for client: {}", clientId);
            boolean updated = userService.updateCredentials(clientId, apiKey, apiSecret);

            if (updated) {
                response.put("success", true);
                response.put("message", "Credentials updated successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Error updating credentials for {}: {}", clientId, e.getMessage());
            response.put("success", false);
            response.put("message", "Failed to update credentials: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Delete user account
     * DELETE /api/users/{clientId}
     */
    @DeleteMapping("/{clientId}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable String clientId) {
        Map<String, Object> response = new HashMap<>();

        try {
            logger.info("Deleting user: {}", clientId);
            boolean deleted = userService.deleteUser(clientId);

            if (deleted) {
                response.put("success", true);
                response.put("message", "User deleted successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Error deleting user {}: {}", clientId, e.getMessage());
            response.put("success", false);
            response.put("message", "Failed to delete user: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Reset user's daily blocking status
     * PUT /api/users/{clientId}/reset-daily-block
     */
    @PutMapping("/{clientId}/reset-daily-block")
    public ResponseEntity<Map<String, Object>> resetDailyBlock(@PathVariable String clientId) {
        Map<String, Object> response = new HashMap<>();

        try {
            logger.info("Resetting daily block for client: {}", clientId);
            boolean reset = userService.resetDailyBlock(clientId);

            if (reset) {
                response.put("success", true);
                response.put("message", "Daily block reset successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Error resetting daily block for {}: {}", clientId, e.getMessage());
            response.put("success", false);
            response.put("message", "Failed to reset daily block: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Validate user's API credentials with Architect
     * POST /api/users/{clientId}/validate-credentials
     */
    @PostMapping("/{clientId}/validate-credentials")
    public ResponseEntity<Map<String, Object>> validateCredentials(@PathVariable String clientId) {
        Map<String, Object> response = new HashMap<>();

        try {
            logger.info("Validating credentials for client: {}", clientId);
            boolean valid = userService.validateUserCredentials(clientId);

            response.put("success", true);
            response.put("valid", valid);
            response.put("message", valid ? "Credentials are valid" : "Credentials are invalid");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error validating credentials for {}: {}", clientId, e.getMessage());
            response.put("success", false);
            response.put("valid", false);
            response.put("message", "Failed to validate credentials: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ========== PRIVATE METHODS ==========

    /**
     * Convert RiskLimitDto to RiskLimit entity
     */
    private RiskLimit convertRiskLimit(UserRegistrationRequest.RiskLimitDto dto) {
        if (!dto.isValid()) {
            throw new IllegalArgumentException("Invalid risk limit: " + dto.getType() + "/" + dto.getValue());
        }

        // Create RiskLimit with type string (percentage or absolute) and value
        return new RiskLimit(dto.getType(), java.math.BigDecimal.valueOf(dto.getValue()));
    }
}







