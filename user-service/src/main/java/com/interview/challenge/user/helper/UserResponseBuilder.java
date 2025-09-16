package com.interview.challenge.user.helper;

import com.interview.challenge.shared.model.ClientConfiguration;
import com.interview.challenge.user.constants.UserConstants;
import com.interview.challenge.user.dto.UserResponse;

import java.util.HashMap;
import java.util.Map;

public class UserResponseBuilder {

    /**
     * Build UserResponse from ClientConfiguration
     */
    public static UserResponse fromClientConfiguration(ClientConfiguration config) {
        return UserResponse.builder()
                .clientId(config.getClientId())
                .initialBalance(config.getInitialBalance())
                .maxRisk(config.getMaxRisk())
                .dailyRisk(config.getDailyRisk())
                .dailyBlocked(config.isDailyBlocked())
                .permanentBlocked(config.isPermanentBlocked())
                .canTrade(config.canTrade())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    /**
     * Build registration success response
     */
    public static Map<String, Object> buildRegistrationResponse(ClientConfiguration user) {
        Map<String, Object> response = new HashMap<>();
        response.put(UserConstants.KEY_SUCCESS, true);
        response.put(UserConstants.KEY_MESSAGE, UserConstants.MSG_USER_REGISTERED);
        response.put(UserConstants.KEY_CLIENT_ID, user.getClientId());
        response.put(UserConstants.KEY_INITIAL_BALANCE, user.getInitialBalance());
        response.put(UserConstants.KEY_MAX_RISK, user.getMaxRisk());
        response.put(UserConstants.KEY_DAILY_RISK, user.getDailyRisk());
        response.put(UserConstants.KEY_CREATED_AT, user.getCreatedAt());
        return response;
    }

    /**
     * Build user details response
     */
    public static Map<String, Object> buildUserDetailsResponse(ClientConfiguration user) {
        Map<String, Object> response = new HashMap<>();
        response.put(UserConstants.KEY_SUCCESS, true);
        response.put(UserConstants.KEY_CLIENT_ID, user.getClientId());
        response.put(UserConstants.KEY_INITIAL_BALANCE, user.getInitialBalance());
        response.put(UserConstants.KEY_MAX_RISK, user.getMaxRisk());
        response.put(UserConstants.KEY_DAILY_RISK, user.getDailyRisk());
        response.put(UserConstants.KEY_DAILY_BLOCKED, user.isDailyBlocked());
        response.put(UserConstants.KEY_PERMANENT_BLOCKED, user.isPermanentBlocked());
        response.put(UserConstants.KEY_CAN_TRADE, user.canTrade());
        response.put(UserConstants.KEY_CREATED_AT, user.getCreatedAt());
        response.put(UserConstants.KEY_UPDATED_AT, user.getUpdatedAt());
        return response;
    }

    /**
     * Build risk limits update response
     */
    public static Map<String, Object> buildRiskLimitsUpdateResponse(ClientConfiguration user) {
        Map<String, Object> response = new HashMap<>();
        response.put(UserConstants.KEY_SUCCESS, true);
        response.put(UserConstants.KEY_MESSAGE, UserConstants.MSG_RISK_LIMITS_UPDATED);
        response.put(UserConstants.KEY_CLIENT_ID, user.getClientId());
        response.put(UserConstants.KEY_MAX_RISK, user.getMaxRisk());
        response.put(UserConstants.KEY_DAILY_RISK, user.getDailyRisk());
        response.put(UserConstants.KEY_UPDATED_AT, user.getUpdatedAt());
        return response;
    }

    /**
     * Build block user response
     */
    public static Map<String, Object> buildBlockUserResponse(String clientId, String reason, boolean isDaily) {
        Map<String, Object> response = new HashMap<>();
        response.put(UserConstants.KEY_SUCCESS, true);
        response.put(UserConstants.KEY_MESSAGE, isDaily ?
            UserConstants.MSG_USER_BLOCKED_DAILY :
            UserConstants.MSG_USER_BLOCKED_PERMANENTLY);
        response.put(UserConstants.KEY_CLIENT_ID, clientId);
        response.put(UserConstants.KEY_REASON, reason);
        return response;
    }

    /**
     * Build can trade response
     */
    public static Map<String, Object> buildCanTradeResponse(String clientId, boolean canTrade) {
        Map<String, Object> response = new HashMap<>();
        response.put(UserConstants.KEY_SUCCESS, true);
        response.put(UserConstants.KEY_CLIENT_ID, clientId);
        response.put(UserConstants.KEY_CAN_TRADE, canTrade);
        return response;
    }

    /**
     * Build error response
     */
    public static Map<String, Object> buildErrorResponse(String errorCode, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put(UserConstants.KEY_SUCCESS, false);
        response.put(UserConstants.KEY_ERROR, errorCode);
        response.put(UserConstants.KEY_MESSAGE, message);
        return response;
    }

    /**
     * Build success response with message
     */
    public static Map<String, Object> buildSuccessResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put(UserConstants.KEY_SUCCESS, true);
        response.put(UserConstants.KEY_MESSAGE, message);
        return response;
    }
}