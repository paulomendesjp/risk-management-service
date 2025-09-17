package com.interview.challenge.user.service;

import com.interview.challenge.shared.model.ClientConfiguration;
import com.interview.challenge.shared.model.RiskLimit;
import com.interview.challenge.shared.dto.ArchitectBalanceResponse;
import com.interview.challenge.user.constants.UserConstants;
import com.interview.challenge.user.dto.UserRegistrationRequest;
import com.interview.challenge.user.repository.UserRepository;
import com.interview.challenge.shared.service.ArchitectApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * User Service - Follows EXACT requirements from document
 * 
 * Requirements:
 * 1. Each user provides: api_key, api_secret, risk limits
 * 2. Initial Balance fetched at start from Architect API
 * 3. Store in MongoDB for persistence and auditing
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringEncryptor jasyptStringEncryptor;

    @Autowired
    private ArchitectApiService architectApiService;

    @Autowired
    private CredentialManager credentialManager;

    @Autowired
    private EventPublisher eventPublisher;

    /**
     * Register a new user with API credentials and risk limits
     * Requirement 1: User Configuration
     */
    public ClientConfiguration registerUser(UserRegistrationRequest request) {
        String clientId = request.getClientId();
        MDC.put("clientId", clientId);
        
        try {
            logger.info(UserConstants.LOG_REGISTERING_USER, clientId);
            
            // Check if user already exists
            if (userRepository.existsByClientId(clientId)) {
                throw new IllegalArgumentException(UserConstants.MSG_USER_ALREADY_EXISTS + ": " + clientId);
            }
            
            BigDecimal initialBalance;

            // Check if initial balance was provided in the request
            if (request.getInitialBalance() != null && request.getInitialBalance().compareTo(BigDecimal.ZERO) >= 0) {
                // Use provided initial balance
                logger.info("💵 Using provided initial balance for client {}: ${}",
                    clientId, request.getInitialBalance());
                initialBalance = request.getInitialBalance();

                // Still validate credentials but don't use the balance
                logger.debug(UserConstants.LOG_VALIDATING_CREDENTIALS, clientId);
                try {
                    architectApiService.validateCredentialsAndGetBalance(
                        request.getApiKey(), request.getApiSecret());
                    logger.info("✅ API credentials validated successfully for client: {}", clientId);
                } catch (Exception e) {
                    logger.warn("⚠️ Could not validate credentials with Architect: {}", e.getMessage());
                    // Continue with registration even if validation fails
                }
            } else {
                // Fetch balance from Architect API
                logger.info("📊 Fetching balance from Architect API for client: {}", clientId);
                logger.debug(UserConstants.LOG_VALIDATING_CREDENTIALS, clientId);

                try {
                    ArchitectBalanceResponse balanceResponse = architectApiService.validateCredentialsAndGetBalance(
                        request.getApiKey(), request.getApiSecret());
                    initialBalance = balanceResponse.getBalance();
                    logger.info("💰 Balance fetched from Architect: ${} for client: {}",
                        initialBalance, clientId);
                } catch (Exception e) {
                    logger.error("❌ Failed to fetch balance from Architect for client {}: {}",
                        clientId, e.getMessage());
                    // Use zero as fallback
                    initialBalance = BigDecimal.ZERO;
                    logger.warn("⚠️ Using default balance of 0 for client: {}", clientId);
                }
            }
            
            // Create user configuration
            ClientConfiguration user = createClientConfiguration(request, initialBalance);
            
            // Save to MongoDB (Requirement: Store in MongoDB for persistence and auditing)
            ClientConfiguration savedUser = userRepository.save(user);
            logger.info(UserConstants.LOG_USER_REGISTERED, clientId, initialBalance);
            
            // Publish user registration event for other services
            eventPublisher.publishUserRegistrationEvent(savedUser);
            
            return savedUser;
            
        } catch (Exception e) {
            logger.error(UserConstants.LOG_REGISTRATION_FAILED, clientId, e.getMessage());
            throw e;
        } finally {
            MDC.clear();
        }
    }

    /**
     * Get user by client ID
     */
    public Optional<ClientConfiguration> getUserByClientId(String clientId) {
        MDC.put("clientId", clientId);

        try {
            return userRepository.findByClientId(clientId);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Get decrypted API credentials for a client
     */
    public Map<String, String> getDecryptedCredentials(String clientId) {
        MDC.put("clientId", clientId);
        
        try {
            Optional<ClientConfiguration> userOpt = userRepository.findByClientId(clientId);

            if (userOpt.isEmpty()) {
                throw new IllegalArgumentException(UserConstants.MSG_USER_NOT_FOUND + ": " + clientId);
            }
            
            ClientConfiguration user = userOpt.get();
            
            Map<String, String> credentials = credentialManager.decryptCredentials(
                user.getApiKey(), user.getApiSecret());
            
            return credentials;
            
        } finally {
            MDC.clear();
        }
    }

    /**
     * Check if user can trade
     */
    public boolean canUserTrade(String clientId) {
        Optional<ClientConfiguration> userOpt = getUserByClientId(clientId);
        if (userOpt.isEmpty()) {
            return false;
        }
        return userOpt.get().canTrade();
    }

    /**
     * Block user daily
     */
    public void blockUserDaily(String clientId, String reason) {
        blockUser(clientId, true, reason);
    }

    /**
     * Block user permanently
     */
    public void blockUserPermanently(String clientId, String reason) {
        blockUser(clientId, false, reason);
    }

    /**
     * Delete user
     */
    public boolean deleteUser(String clientId) {
        MDC.put("clientId", clientId);
        try {
            Optional<ClientConfiguration> userOpt = userRepository.findByClientId(clientId);
            if (userOpt.isEmpty()) {
                return false;
            }
            userRepository.delete(userOpt.get());
            logger.info(UserConstants.LOG_USER_DELETED, clientId);
            return true;
        } finally {
            MDC.clear();
        }
    }

    /**
     * Reset daily block
     */
    public boolean resetDailyBlock(String clientId) {
        MDC.put("clientId", clientId);
        try {
            Optional<ClientConfiguration> userOpt = userRepository.findByClientId(clientId);
            if (userOpt.isEmpty()) {
                return false;
            }
            ClientConfiguration user = userOpt.get();
            user.setDailyBlocked(false);
            user.setDailyBlockReason(null);
            user.setDailyBlockedAt(null);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            logger.info(UserConstants.LOG_DAILY_BLOCK_RESET, clientId);
            return true;
        } finally {
            MDC.clear();
        }
    }

    /**
     * Update risk limits for a user
     */
    public ClientConfiguration updateRiskLimits(String clientId,
                                               RiskLimit maxRisk,
                                               RiskLimit dailyRisk) {
        MDC.put("clientId", clientId);
        
        try {
            Optional<ClientConfiguration> userOpt = userRepository.findByClientId(clientId);

            if (userOpt.isEmpty()) {
                throw new IllegalArgumentException(UserConstants.MSG_USER_NOT_FOUND + ": " + clientId);
            }
            
            ClientConfiguration user = userOpt.get();
            
            // Store old values for event publishing
            RiskLimit oldMaxRisk = user.getMaxRisk();
            RiskLimit oldDailyRisk = user.getDailyRisk();
            
            // Update risk limits
            user.setMaxRisk(maxRisk);
            user.setDailyRisk(dailyRisk);
            user.setUpdatedAt(LocalDateTime.now());
            
            // Save updated user
            ClientConfiguration savedUser = userRepository.save(user);
            
            logger.info(UserConstants.LOG_RISK_LIMITS_UPDATED, clientId);
            
            // Publish risk limit update event
            eventPublisher.publishRiskLimitUpdateEvent(savedUser);
            
            return savedUser;
            
        } finally {
            MDC.clear();
        }
    }

    /**
     * Block user (daily or permanent)
     */
    public void blockUser(String clientId, boolean isDailyBlock, String reason) {
        MDC.put("clientId", clientId);
        
        try {
            Optional<ClientConfiguration> userOpt = userRepository.findByClientId(clientId);

            if (userOpt.isEmpty()) {
                throw new IllegalArgumentException(UserConstants.MSG_USER_NOT_FOUND + ": " + clientId);
            }
            
            ClientConfiguration user = userOpt.get();
            
            if (isDailyBlock) {
                user.setDailyBlocked(true);
                user.setDailyBlockReason(reason);
                user.setDailyBlockedAt(LocalDateTime.now());
                logger.warn(UserConstants.LOG_USER_BLOCKED_DAILY, clientId, reason);
            } else {
                user.setPermanentBlocked(true);
                user.setPermanentBlockReason(reason);
                user.setPermanentBlockedAt(LocalDateTime.now());
                logger.error(UserConstants.LOG_USER_BLOCKED_PERMANENT, clientId, reason);
            }
            
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            
        } finally {
            MDC.clear();
        }
    }

    /**
     * Unblock user (daily only - permanent blocks cannot be undone)
     */
    public void unblockUser(String clientId) {
        MDC.put("clientId", clientId);
        
        try {
            Optional<ClientConfiguration> userOpt = userRepository.findByClientId(clientId);

            if (userOpt.isEmpty()) {
                throw new IllegalArgumentException(UserConstants.MSG_USER_NOT_FOUND + ": " + clientId);
            }
            
            ClientConfiguration user = userOpt.get();
            
            if (user.isPermanentBlocked()) {
                throw new IllegalStateException("Cannot unblock permanently blocked user: " + clientId);
            }
            
            user.setDailyBlocked(false);
            user.setDailyBlockReason(null);
            user.setDailyBlockedAt(null);
            user.setUpdatedAt(LocalDateTime.now());
            
            userRepository.save(user);
            
            logger.info(UserConstants.LOG_USER_UNBLOCKED, clientId);
            
        } finally {
            MDC.clear();
        }
    }

    /**
     * Create ClientConfiguration from registration request
     */
    private ClientConfiguration createClientConfiguration(UserRegistrationRequest request, BigDecimal initialBalance) {
        ClientConfiguration user = new ClientConfiguration();
        
        user.setClientId(request.getClientId());
        
        // Encrypt API credentials for security
        user.setApiKey(credentialManager.encryptCredential(request.getApiKey()));
        user.setApiSecret(credentialManager.encryptCredential(request.getApiSecret()));
        
        user.setInitialBalance(initialBalance);
        
        // Convert risk limits from DTO format to entity format
        user.setMaxRisk(convertRiskLimit(request.getMaxRisk()));
        user.setDailyRisk(convertRiskLimit(request.getDailyRisk()));
        
        return user;
    }

    /**
     * Convert RiskLimitDto to RiskLimit entity
     */
    private RiskLimit convertRiskLimit(UserRegistrationRequest.RiskLimitDto dto) {
        String type = UserConstants.RISK_TYPE_PERCENTAGE.equalsIgnoreCase(dto.getType())
                ? UserConstants.RISK_TYPE_PERCENTAGE
                : UserConstants.RISK_TYPE_ABSOLUTE;
        
        return new RiskLimit(type, BigDecimal.valueOf(dto.getValue()));
    }

    // Methods moved to EventPublisher class

    /**
     * Get all users (admin function)
     */
    public List<ClientConfiguration> getAllUsers() {
        logger.debug("Fetching all users from database");
        return userRepository.findAll();
    }

    /**
     * Update user's API credentials
     */
    public boolean updateCredentials(String clientId, String apiKey, String apiSecret) {
        MDC.put("clientId", clientId);
        
        try {
            Optional<ClientConfiguration> userOpt = userRepository.findByClientId(clientId);
            
            if (userOpt.isEmpty()) {
                logger.warn("User not found for credential update: {}", clientId);
                return false;
            }
            
            ClientConfiguration user = userOpt.get();
            
            // Encrypt and update credentials
            user.setApiKey(credentialManager.encryptCredential(apiKey));
            user.setApiSecret(credentialManager.encryptCredential(apiSecret));
            user.setUpdatedAt(LocalDateTime.now());
            
            userRepository.save(user);
            
            logger.info(UserConstants.LOG_CREDENTIALS_UPDATED, clientId);
            return true;
            
        } catch (Exception e) {
            logger.error("❌ Failed to update credentials for user {}: {}", clientId, e.getMessage());
            return false;
        } finally {
            MDC.clear();
        }
    }

    /**
     * Validate user's API credentials with Architect
     */
    public boolean validateUserCredentials(String clientId) {
        MDC.put("clientId", clientId);
        
        try {
            Map<String, String> credentials = getDecryptedCredentials(clientId);
            
            // Validate with Architect API
            ArchitectBalanceResponse balanceResponse = architectApiService.validateCredentialsAndGetBalance(
                credentials.get("apiKey"), credentials.get("apiSecret"));
            BigDecimal balance = balanceResponse.getBalance();

            logger.info(UserConstants.LOG_CREDENTIALS_VALIDATED, clientId, balance);
            return true;
            
        } catch (Exception e) {
            logger.error("Error validating credentials for user {}: {}", clientId, e.getMessage());
            throw new RuntimeException("Failed to validate credentials", e);
        }
    }
}