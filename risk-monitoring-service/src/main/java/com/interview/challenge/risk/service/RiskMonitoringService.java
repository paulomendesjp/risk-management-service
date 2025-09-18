package com.interview.challenge.risk.service;

import com.interview.challenge.risk.constants.RiskConstants;
import com.interview.challenge.risk.enums.RiskLimitType;
import com.interview.challenge.risk.enums.ViolationType;
import com.interview.challenge.risk.model.AccountMonitoring;
import com.interview.challenge.risk.model.RiskStatus;
import com.interview.challenge.risk.repository.AccountMonitoringRepository;
import com.interview.challenge.risk.websocket.RiskWebSocketHandler;
import com.interview.challenge.shared.dto.ArchitectBalanceResponse;
import com.interview.challenge.shared.model.ClientConfiguration;
import com.interview.challenge.shared.model.RiskLimit;
import com.interview.challenge.shared.model.RiskType;
import com.interview.challenge.shared.service.ArchitectApiService;
import com.interview.challenge.shared.event.RiskViolationEvent;
import com.interview.challenge.shared.event.BalanceUpdateEvent;
import com.interview.challenge.shared.event.NotificationEvent;
import com.interview.challenge.shared.audit.MandatoryAuditLogger;
import com.interview.challenge.shared.client.UserServiceClient;
import com.interview.challenge.shared.client.PositionServiceClient;
import com.interview.challenge.shared.dto.ClosePositionsResult;
import org.springframework.web.client.RestTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Core Risk Monitoring Service
 * 
 * Responsibilities:
 * - Real-time balance monitoring
 * - Risk limit enforcement
 * - Automated risk actions (position closing, blocking)
 * - WebSocket broadcasting
 * - Event publishing via RabbitMQ
 */
@Service
public class RiskMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(RiskMonitoringService.class);

    @Value("${architect.bridge.endpoint:http://localhost:8090}")
    private String architectBridgeEndpoint;

    @Autowired
    private AccountMonitoringRepository accountMonitoringRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ArchitectApiService architectApiService;

    @Autowired
    private RiskWebSocketHandler webSocketHandler;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MandatoryAuditLogger mandatoryAuditLogger;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private PositionServiceClient positionServiceClient;

    @Autowired
    private RiskCalculator riskCalculator;

    @Autowired
    private RiskActionExecutor riskActionExecutor;

    @Autowired
    private BalanceProcessor balanceProcessor;

    @Autowired
    private NotificationPublisher notificationPublisher;
    
    @Value("${risk.monitoring.balance-check-interval:30000}")
    private long balanceCheckInterval;
    
    @Value("${risk.monitoring.risk-check-interval:60000}")
    private long riskCheckInterval;
    
    @Value("${risk.monitoring.daily-reset-time:09:00}")
    private String dailyResetTime;
    
    @Value("${risk.monitoring.max-concurrent-checks:10}")
    private int maxConcurrentChecks;

    /**
     * Initialize monitoring for a new client
     */
    public AccountMonitoring initializeMonitoring(String clientId, BigDecimal initialBalance) {
        logger.info("Initializing risk monitoring for client: {} with initial balance: {}", 
                   clientId, initialBalance);
        
        Optional<AccountMonitoring> existing = accountMonitoringRepository.findByClientId(clientId);
        if (existing.isPresent()) {
            logger.info("Monitoring already exists for client: {}, updating initial balance", clientId);
            AccountMonitoring monitoring = existing.get();
            monitoring.setInitialBalance(initialBalance);
            monitoring.setUpdatedAt(LocalDateTime.now());
            return accountMonitoringRepository.save(monitoring);
        }
        
        AccountMonitoring monitoring = new AccountMonitoring(clientId, initialBalance);
        AccountMonitoring saved = accountMonitoringRepository.save(monitoring);
        
        // Broadcast initialization
        broadcastAccountStatus(saved);
        
        logger.info("Risk monitoring initialized for client: {}", clientId);
        return saved;
    }

    /**
     * Update account balance and check risk limits
     */
    @Async
    public CompletableFuture<Void> updateBalanceAndCheckRisk(String clientId, String apiKey, String apiSecret) {
        return CompletableFuture.runAsync(() -> {
            try {
                logger.debug("Updating balance and checking risk for client: {}", clientId);
                
                // Get current balance from Architect API
                ArchitectBalanceResponse balanceResponse = architectApiService.getAccountBalance(apiKey, apiSecret);
                BigDecimal currentBalance = balanceResponse.getTotalBalance();
                BigDecimal unrealizedPnl = balanceResponse.getRealizedPnl();
                
                // Update monitoring data
                Optional<AccountMonitoring> optMonitoring = accountMonitoringRepository.findByClientId(clientId);
                if (optMonitoring.isEmpty()) {
                    logger.warn("No monitoring data found for client: {}, initializing with current balance", clientId);
                    initializeMonitoring(clientId, currentBalance);
                    return;
                }
                
                AccountMonitoring monitoring = optMonitoring.get();
                monitoring.updateBalance(currentBalance, unrealizedPnl);
                
                // Check risk limits
                RiskCheckResult riskResult = checkRiskLimits(monitoring, clientId);
                
                // Save updated monitoring data
                accountMonitoringRepository.save(monitoring);
                
                // Handle risk violations
                if (riskResult.hasViolation()) {
                    handleRiskViolation(monitoring, riskResult, apiKey, apiSecret);
                }
                
                // Broadcast updates
                broadcastBalanceUpdate(monitoring);
                broadcastAccountStatus(monitoring);
                
                // Publish balance update event
                publishBalanceUpdateEvent(monitoring);
                
                logger.debug("Balance and risk check completed for client: {}", clientId);
                
            } catch (Exception e) {
                logger.error("Error updating balance and checking risk for client {}: {}", clientId, e.getMessage(), e);
            }
        });
    }

    /**
     * Check risk limits for an account
     */
    public RiskCheckResult checkRiskLimits(AccountMonitoring monitoring, String clientId) {
        RiskCheckResult result = new RiskCheckResult();
        
        try {
            // Get client configuration with risk limits
            ClientConfiguration config = getClientConfiguration(clientId);
            if (config == null) {
                logger.warn("No configuration found for client: {}, skipping risk check", clientId);
                return result;
            }
            
            // Check if account is already blocked
            if (!monitoring.canTrade()) {
                logger.debug("Account {} is already blocked, skipping risk check", clientId);
                return result;
            }
            
            BigDecimal currentLoss = monitoring.getCurrentLoss();
            BigDecimal dailyLoss = monitoring.getDailyLoss();
            
            // Check Max Risk Limit
            if (config.getMaxRisk() != null) {
                BigDecimal maxRiskThreshold = calculateRiskThreshold(
                    config.getMaxRisk(), monitoring.getInitialBalance());
                
                if (riskCalculator.isRiskLimitExceeded(currentLoss, maxRiskThreshold)) {
                    result.setMaxRiskViolated(true);
                    result.setMaxRiskLoss(currentLoss);
                    result.setMaxRiskThreshold(maxRiskThreshold);
                    logger.warn("MAX RISK VIOLATION for client {}: Loss {} >= Threshold {}",
                              clientId, currentLoss, maxRiskThreshold);
                }
            }
            
            // Check Daily Risk Limit (only if max risk not violated)
            if (!result.isMaxRiskViolated() && config.getDailyRisk() != null) {
                BigDecimal dailyRiskThreshold = calculateRiskThreshold(
                    config.getDailyRisk(), monitoring.getDailyStartBalance());
                
                if (riskCalculator.isRiskLimitExceeded(dailyLoss, dailyRiskThreshold)) {
                    result.setDailyRiskViolated(true);
                    result.setDailyRiskLoss(dailyLoss);
                    result.setDailyRiskThreshold(dailyRiskThreshold);
                    logger.warn("DAILY RISK VIOLATION for client {}: Daily Loss {} >= Threshold {}",
                              clientId, dailyLoss, dailyRiskThreshold);
                }
            }
            
            // Update risk status
            if (result.isMaxRiskViolated()) {
                monitoring.setRiskStatus(RiskStatus.MAX_RISK_TRIGGERED);
            } else if (result.isDailyRiskViolated()) {
                monitoring.setRiskStatus(RiskStatus.DAILY_RISK_TRIGGERED);
            } else {
                // Check warning thresholds (80% of limits)
                boolean nearMaxRisk = config.getMaxRisk() != null &&
                    riskCalculator.isNearRiskLimit(currentLoss, calculateRiskThreshold(config.getMaxRisk(), monitoring.getInitialBalance()));
                boolean nearDailyRisk = config.getDailyRisk() != null &&
                    riskCalculator.isNearRiskLimit(dailyLoss, calculateRiskThreshold(config.getDailyRisk(), monitoring.getDailyStartBalance()));
                
                if (nearMaxRisk || nearDailyRisk) {
                    monitoring.setRiskStatus(RiskStatus.MONITORING_ERROR);
                } else {
                    monitoring.setRiskStatus(RiskStatus.NORMAL);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error checking risk limits for client {}: {}", clientId, e.getMessage(), e);
        }
        
        return result;
    }

    /**
     * Handle risk violation - close positions and block account
     */
    private void handleRiskViolation(AccountMonitoring monitoring, RiskCheckResult riskResult, 
                                   String apiKey, String apiSecret) {
        String clientId = monitoring.getClientId();
        
        try {
            if (riskResult.isMaxRiskViolated()) {
                riskActionExecutor.executeRiskActions(monitoring, ViolationType.MAX_RISK,
                    riskResult.getMaxRiskLoss(), riskResult.getMaxRiskThreshold());
            } else if (riskResult.isDailyRiskViolated()) {
                riskActionExecutor.executeRiskActions(monitoring, ViolationType.DAILY_RISK,
                    riskResult.getDailyRiskLoss(), riskResult.getDailyRiskThreshold());
            }
            
        } catch (Exception e) {
            logger.error("Error handling risk violation for client {}: {}", clientId, e.getMessage(), e);
        }
    }

    /**
     * Reset daily tracking for all accounts (scheduled job)
     */
    @Scheduled(cron = RiskConstants.DAILY_RESET_CRON)
    public void performDailyReset() {
        logger.info("Starting daily risk reset for all accounts");
        
        try {
            LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);
            List<AccountMonitoring> accountsToReset = 
                accountMonitoringRepository.findAccountsNeedingDailyReset(startOfDay);
            
            logger.info("Found {} accounts needing daily reset", accountsToReset.size());
            
            for (AccountMonitoring monitoring : accountsToReset) {
                try {
                    monitoring.resetDailyTracking();
                    accountMonitoringRepository.save(monitoring);
                    
                    // Broadcast reset notification
                    webSocketHandler.broadcastRiskStatus(monitoring.getClientId(), Map.of(
                        "event", "daily_reset",
                        "status", "reset_completed",
                        "canTrade", monitoring.canTrade(),
                        "dailyPnl", monitoring.getDailyPnl()
                    ));
                    
                    logger.debug("Daily reset completed for client: {}", monitoring.getClientId());
                    
                } catch (Exception e) {
                    logger.error("Error resetting daily tracking for client {}: {}", 
                               monitoring.getClientId(), e.getMessage());
                }
            }
            
            logger.info("Daily risk reset completed for {} accounts", accountsToReset.size());
            
        } catch (Exception e) {
            logger.error("Error during daily risk reset: {}", e.getMessage(), e);
        }
    }

    /**
     * Periodic risk check for all active accounts
     */
    @Scheduled(fixedDelayString = "${risk.monitoring.risk-check-interval:60000}")
    public void performPeriodicRiskCheck() {
        logger.debug("Starting periodic risk check for all accounts");

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusSeconds(riskCheckInterval / 1000);
            List<AccountMonitoring> accountsToCheck =
                accountMonitoringRepository.findAccountsNeedingRiskCheck(cutoffTime);

            if (!accountsToCheck.isEmpty()) {
                logger.debug("Found {} accounts needing risk check", accountsToCheck.size());

                // Process accounts in batches to avoid overwhelming the system
                accountsToCheck.stream()
                    .limit(maxConcurrentChecks)
                    .forEach(monitoring -> {
                        try {
                            // Get client configuration for API credentials
                            ClientConfiguration config = getClientConfiguration(monitoring.getClientId());
                            if (config != null && config.getApiKey() != null) {
                                updateBalanceAndCheckRisk(monitoring.getClientId(),
                                    config.getApiKey(), config.getApiSecret());
                            }
                        } catch (Exception e) {
                            logger.error("Error in periodic risk check for client {}: {}",
                                       monitoring.getClientId(), e.getMessage());
                        }
                    });
            }

        } catch (Exception e) {
            logger.error("Error during periodic risk check: {}", e.getMessage(), e);
        }
    }

    /**
     * 💰 PERIODIC BALANCE POLLING FROM ARCHITECT-BRIDGE
     * Polls architect-bridge API to get real-time balance updates
     * Replaces WebSocket streaming with scheduled polling
     */
    @Scheduled(fixedDelayString = "${risk.monitoring.balance-poll-interval:30000}")
    public void performPeriodicBalancePoll() {
        logger.info("💰 Starting periodic balance polling from architect-bridge");

        try {
            // Get all active monitoring accounts
            List<AccountMonitoring> activeAccounts = accountMonitoringRepository.findAll().stream()
                .filter(AccountMonitoring::canTrade)
                .limit(maxConcurrentChecks)
                .toList();

            logger.debug("Found {} active accounts to poll balances", activeAccounts.size());

            for (AccountMonitoring monitoring : activeAccounts) {
                try {
                    String clientId = monitoring.getClientId();

                    // Get client configuration for API credentials
                    ClientConfiguration config = getClientConfiguration(clientId);
                    if (config == null || config.getApiKey() == null) {
                        logger.warn("No configuration found for client {}, skipping balance poll", clientId);
                        continue;
                    }

                    logger.debug("📊 Polling balance for client {}", clientId);

                    // Poll balance from architect-bridge via ArchitectApiService
                    ArchitectBalanceResponse balanceResponse = architectApiService.getAccountBalance(
                        config.getApiKey(), config.getApiSecret());

                    logger.info("🔍 DEBUG POLLING for {}: balanceResponse={}, totalBalance={}",
                        clientId,
                        balanceResponse != null ? "NOT NULL" : "NULL",
                        balanceResponse != null ? balanceResponse.getTotalBalance() : "N/A");

                    if (balanceResponse != null && balanceResponse.getTotalBalance() != null) {
                        BigDecimal newBalance = balanceResponse.getTotalBalance();
                        BigDecimal previousBalance = monitoring.getCurrentBalance();

                        logger.info("📊 BALANCE COMPARISON for {}:", clientId);
                        logger.info("  - Previous Balance: ${}", previousBalance);
                        logger.info("  - New Balance: ${}", newBalance);
                        logger.info("  - Are they equal? {}",
                            previousBalance != null ? previousBalance.compareTo(newBalance) == 0 : "previous is null");

                        // Check if balance has changed
                        if (previousBalance == null || newBalance.compareTo(previousBalance) != 0) {
                            logger.info("💵 Balance change detected for client {}: {} -> {}",
                                      clientId, previousBalance, newBalance);

                            // Create balance update event
                            BalanceUpdateEvent balanceUpdate = new BalanceUpdateEvent();
                            balanceUpdate.setClientId(clientId);
                            balanceUpdate.setNewBalance(newBalance);
                            balanceUpdate.setPreviousBalance(previousBalance);
                            balanceUpdate.setSource("polling");
                            balanceUpdate.setTimestamp(LocalDateTime.now());

                            // Process the balance update
                            processBalanceUpdate(balanceUpdate);
                        } else {
                            logger.info("📍 NO BALANCE CHANGE for client {}: still ${}", clientId, previousBalance);
                        }
                    } else {
                        logger.warn("⚠️ NULL or INVALID balance response for client {}", clientId);
                    }

                } catch (Exception e) {
                    logger.error("Error polling balance for client {}: {}",
                               monitoring.getClientId(), e.getMessage());
                }
            }

            logger.debug("✅ Completed periodic balance polling");

        } catch (Exception e) {
            logger.error("Error during periodic balance poll: {}", e.getMessage(), e);
        }
    }

    /**
     * Get account monitoring data
     */
    public Optional<AccountMonitoring> getAccountMonitoring(String clientId) {
        return accountMonitoringRepository.findByClientId(clientId);
    }

    /**
     * Get all account monitoring data
     */
    public List<AccountMonitoring> getAllAccountMonitoring() {
        return accountMonitoringRepository.findAll();
    }

    /**
     * Get monitoring statistics
     */
    public Map<String, Object> getMonitoringStatistics() {
        long totalAccounts = accountMonitoringRepository.count();
        long blockedAccounts = accountMonitoringRepository.countBlockedAccounts();
        long safeAccounts = accountMonitoringRepository.countByRiskStatus(RiskStatus.NORMAL);
        long warningAccounts = accountMonitoringRepository.countByRiskStatus(RiskStatus.MONITORING_ERROR);
        
        return Map.of(
            "totalAccounts", totalAccounts,
            "blockedAccounts", blockedAccounts,
            "safeAccounts", safeAccounts,
            "warningAccounts", warningAccounts,
            "activeConnections", webSocketHandler.getTotalActiveConnections(),
            "lastUpdate", LocalDateTime.now()
        );
    }

    // ========== PRIVATE HELPER METHODS ==========

    private BigDecimal calculateRiskThreshold(RiskLimit riskLimit, BigDecimal baseAmount) {
        return riskCalculator.calculateRiskThreshold(riskLimit, baseAmount);
    }
    
    /**
     * 👤 GET USER CONFIGURATION FROM USER SERVICE
     */
    private ClientConfiguration getUserConfiguration(String clientId) {
        try {
            logger.debug("👤 Fetching configuration for client {} from User Service", clientId);
            ClientConfiguration config = userServiceClient.getClientConfiguration(clientId);
            logger.debug("✅ Configuration retrieved for client {}: maxRisk={}, dailyRisk={}", 
                        clientId, config.getMaxRisk().getValue(), config.getDailyRisk().getValue());
            return config;
        } catch (Exception e) {
            logger.error("❌ Error fetching configuration for client {}: {}", clientId, e.getMessage());
            return null;
        }
    }
    
    // Method removed - now handled by RiskActionExecutor
    
    /**
     * 🔧 CREATE DEFAULT CONFIGURATION (FALLBACK)
     */
    private ClientConfiguration createDefaultConfiguration(String clientId) {
        ClientConfiguration defaultConfig = new ClientConfiguration();
        defaultConfig.setClientId(clientId);
        
        // Default max risk: 30% of initial balance
        defaultConfig.setMaxRisk(riskCalculator.createDefaultMaxRiskLimit());

        // Default daily risk: $5000 absolute
        defaultConfig.setDailyRisk(riskCalculator.createDefaultDailyRiskLimit());
        
        logger.warn("⚙️ Using default configuration for client {}: maxRisk=30%, dailyRisk=$5000", clientId);
        
        return defaultConfig;
    }

    private ClientConfiguration getClientConfiguration(String clientId) {
        try {
            // Query MongoDB for client configuration
            Query query = new Query(Criteria.where("clientId").is(clientId));
            return mongoTemplate.findOne(query, ClientConfiguration.class);
        } catch (Exception e) {
            logger.error("Error fetching client configuration for {}: {}", clientId, e.getMessage());
            return null;
        }
    }

    private void broadcastBalanceUpdate(AccountMonitoring monitoring) {
        try {
            Map<String, Object> balanceData = Map.of(
                "currentBalance", monitoring.getCurrentBalance(),
                "totalPnl", monitoring.getTotalPnl(),
                "dailyPnl", monitoring.getDailyPnl(),
                "unrealizedPnl", monitoring.getUnrealizedPnl(),
                "lastUpdate", monitoring.getUpdatedAt()
            );
            
            webSocketHandler.broadcastBalanceUpdate(monitoring.getClientId(), balanceData);
        } catch (Exception e) {
            logger.error("Error broadcasting balance update for client {}: {}", 
                       monitoring.getClientId(), e.getMessage());
        }
    }

    private void broadcastAccountStatus(AccountMonitoring monitoring) {
        try {
            Map<String, Object> statusData = Map.of(
                "riskStatus", monitoring.getRiskStatus(),
                "canTrade", monitoring.canTrade(),
                "dailyBlocked", monitoring.isDailyBlocked(),
                "permanentlyBlocked", monitoring.isPermanentlyBlocked(),
                "currentLoss", monitoring.getCurrentLoss(),
                "dailyLoss", monitoring.getDailyLoss(),
                "lastRiskCheck", monitoring.getLastRiskCheck()
            );
            
            webSocketHandler.broadcastRiskStatus(monitoring.getClientId(), statusData);
        } catch (Exception e) {
            logger.error("Error broadcasting account status for client {}: {}", 
                       monitoring.getClientId(), e.getMessage());
        }
    }

    private void publishRiskViolationEvent(String clientId, ViolationType violationType,
                                         BigDecimal lossAmount, String reason) {
        notificationPublisher.publishRiskViolationEvent(clientId, violationType, lossAmount, reason);
    }

    private void publishBalanceUpdateEvent(AccountMonitoring monitoring) {
        BigDecimal previousBalance = monitoring.getCurrentBalance().subtract(monitoring.getDailyPnl());
        notificationPublisher.publishBalanceUpdate(monitoring.getClientId(),
            monitoring.getCurrentBalance(), previousBalance, RiskConstants.SOURCE_RISK_MONITORING);
    }
    
    /**
     * 📢 PUBLISH NOTIFICATION EVENT TO QUEUE
     *
     * Implements Requirement 5: Notifications
     * - Sends to notification service via RabbitMQ queue
     * - Notification service handles:
     *   - Mandatory system logs
     *   - Optional email notifications
     *   - Optional Slack notifications
     *   - Optional WebSocket push notifications
     */
    private void publishNotificationEvent(NotificationEvent notificationEvent) {
        notificationPublisher.publishNotificationEvent(notificationEvent);
    }

    // ========== INNER CLASSES ==========

    public static class RiskCheckResult {
        private boolean maxRiskViolated = false;
        private boolean dailyRiskViolated = false;
        private BigDecimal maxRiskLoss;
        private BigDecimal maxRiskThreshold;
        private BigDecimal dailyRiskLoss;
        private BigDecimal dailyRiskThreshold;

        public boolean hasViolation() {
            return maxRiskViolated || dailyRiskViolated;
        }

        // Getters and Setters
        public boolean isMaxRiskViolated() { return maxRiskViolated; }
        public void setMaxRiskViolated(boolean maxRiskViolated) { this.maxRiskViolated = maxRiskViolated; }

        public boolean isDailyRiskViolated() { return dailyRiskViolated; }
        public void setDailyRiskViolated(boolean dailyRiskViolated) { this.dailyRiskViolated = dailyRiskViolated; }

        public BigDecimal getMaxRiskLoss() { return maxRiskLoss; }
        public void setMaxRiskLoss(BigDecimal maxRiskLoss) { this.maxRiskLoss = maxRiskLoss; }

        public BigDecimal getMaxRiskThreshold() { return maxRiskThreshold; }
        public void setMaxRiskThreshold(BigDecimal maxRiskThreshold) { this.maxRiskThreshold = maxRiskThreshold; }

        public BigDecimal getDailyRiskLoss() { return dailyRiskLoss; }
        public void setDailyRiskLoss(BigDecimal dailyRiskLoss) { this.dailyRiskLoss = dailyRiskLoss; }

        public BigDecimal getDailyRiskThreshold() { return dailyRiskThreshold; }
        public void setDailyRiskThreshold(BigDecimal dailyRiskThreshold) { this.dailyRiskThreshold = dailyRiskThreshold; }
    }

    // 🌐 WEBSOCKET REAL-TIME MONITORING METHODS

    /**
     * 🚀 START REAL-TIME WEBSOCKET MONITORING
     */
    public boolean startRealTimeMonitoring(String clientId) {
        try {
            logger.info("🚀 Starting real-time WebSocket monitoring for client: {}", clientId);

            ClientConfiguration clientConfig = getClientConfiguration(clientId);
            if (clientConfig == null) {
                logger.error("❌ No client configuration found for: {}", clientId);
                return false;
            }

            String bridgeUrl = architectBridgeEndpoint + RiskConstants.START_MONITORING_ENDPOINT + clientId;

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("X-API-Key", clientConfig.getApiKey());
            headers.set("X-API-Secret", clientConfig.getApiSecret());

            org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>("", headers);

            org.springframework.http.ResponseEntity<Map> response = restTemplate.postForEntity(
                bridgeUrl, request, Map.class
            );

            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            logger.error("❌ Error starting real-time monitoring for client {}: {}", clientId, e.getMessage());
            return false;
        }
    }

    /**
     * 🚀 START REAL-TIME WEBSOCKET MONITORING WITH CREDENTIALS
     * Overloaded method that accepts decrypted API credentials directly
     */
    public boolean startRealTimeMonitoring(String clientId, String apiKey, String apiSecret) {
        try {
            logger.info("🚀 Starting real-time WebSocket monitoring for client: {} with provided credentials", clientId);

            String bridgeUrl = architectBridgeEndpoint + RiskConstants.START_MONITORING_ENDPOINT + clientId;

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            // Python Bridge expects lowercase headers with hyphens
            headers.set(RiskConstants.HEADER_API_KEY, apiKey);
            headers.set(RiskConstants.HEADER_API_SECRET, apiSecret);
            headers.set(RiskConstants.HEADER_CONTENT_TYPE, RiskConstants.CONTENT_TYPE_JSON);

            org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>("", headers);

            org.springframework.http.ResponseEntity<Map> response = restTemplate.postForEntity(
                bridgeUrl, request, Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("✅ WebSocket monitoring started successfully for client: {}", clientId);
                return true;
            } else {
                logger.error("❌ Failed to start WebSocket monitoring for client: {} - Status: {}",
                           clientId, response.getStatusCode());
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ Error starting real-time monitoring for client {}: {}", clientId, e.getMessage());
            return false;
        }
    }

    /**
     * ⏹️ STOP REAL-TIME WEBSOCKET MONITORING
     */
    public boolean stopRealTimeMonitoring(String clientId) {
        try {
            String bridgeUrl = architectBridgeEndpoint + RiskConstants.STOP_MONITORING_ENDPOINT + clientId;
            org.springframework.http.ResponseEntity<Map> response = restTemplate.postForEntity(
                bridgeUrl, null, Map.class
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.error("❌ Error stopping real-time monitoring: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 📡 PROCESS REAL-TIME BALANCE UPDATE FROM WEBSOCKET
     * 
     * Implements requirements:
     * 2. Monitoring - "Continuously fetch account balances in real-time (WebSocket or polling)"
     * 2. Monitoring - "Track daily realized PnL and cumulative PnL against initial balance"
     * 2. Monitoring - "Store daily monitoring data in MongoDB for persistence and auditing"
     * 3. Risk Checks - Check daily and max risk limits and trigger actions
     */
    public void processBalanceUpdate(BalanceUpdateEvent balanceUpdate) {
        try {
            String clientId = balanceUpdate.getClientId();
            BigDecimal newBalance = balanceUpdate.getNewBalance();
            String source = balanceUpdate.getSource();
            
            logger.info("📡 Processing real-time balance update for client {}: ${} from {}", 
                       clientId, newBalance, source);
            
            // 1. Get or initialize monitoring data
            Optional<AccountMonitoring> optMonitoring = accountMonitoringRepository.findByClientId(clientId);
            if (!optMonitoring.isPresent()) {
                logger.info("🆕 Initializing monitoring for new client: {}", clientId);
                initializeMonitoring(clientId, newBalance);
                return;
            }
            
            AccountMonitoring monitoring = optMonitoring.get();
            BigDecimal previousBalance = monitoring.getCurrentBalance();
            
            // 2. Update balance and calculate PnL (Requirement 2: Track daily/cumulative PnL)
            monitoring.updateBalance(newBalance, previousBalance);

            logger.info("📝 BEFORE SAVE - client {}: currentBalance={}, currentLoss={}, dailyLoss={}",
                clientId, monitoring.getCurrentBalance(), monitoring.getCurrentLoss(), monitoring.getDailyLoss());

            // 3. Store in MongoDB for persistence and auditing (Requirement 2)
            AccountMonitoring savedMonitoring = accountMonitoringRepository.save(monitoring);

            logger.info("💾 AFTER SAVE - client {}: currentBalance={}, currentLoss={}, dailyLoss={}",
                clientId, savedMonitoring.getCurrentBalance(), savedMonitoring.getCurrentLoss(), savedMonitoring.getDailyLoss());

            // 4. Check risk limits and take actions if needed (Requirement 3)
            checkRiskLimitsAndTakeActions(savedMonitoring);
            
            // 5. Send notification about balance update (info level)
            publishNotificationEvent(NotificationEvent.balanceUpdate(
                clientId, newBalance, previousBalance, source
            ));
            
            // 6. Broadcast to WebSocket clients for real-time UI updates
            broadcastAccountStatus(savedMonitoring);
            
            logger.debug("✅ Balance update processed successfully for client {}", clientId);
            
        } catch (Exception e) {
            logger.error("❌ Error processing balance update for client {}: {}", 
                        balanceUpdate.getClientId(), e.getMessage());
            
            // Send error notification
            publishNotificationEvent(NotificationEvent.monitoringError(
                balanceUpdate.getClientId(), 
                "Error processing balance update: " + e.getMessage()
            ));
        }
    }

    /**
     * 🚨 HANDLE MONITORING ERROR FROM PYTHON BRIDGE
     */
    public void handleMonitoringError(String clientId, String error) {
        logger.error("🚨 Monitoring error for client {}: {}", clientId, error);
        mandatoryAuditLogger.logMonitoringError(clientId, error);
    }

    /**
     * 🔍 CHECK RISK LIMITS AND TAKE ACTIONS
     * 
     * Implements Requirements 3 & 4:
     * 3. Risk Checks - Daily Risk Trigger & Max Risk Trigger
     * 4. Actions on Breach - Fetch positions, close orders, update database, send notifications
     */
    private void checkRiskLimitsAndTakeActions(AccountMonitoring monitoring) {
        try {
            String clientId = monitoring.getClientId();
            
            // Get user configuration from User Service
            ClientConfiguration clientConfig = getUserConfiguration(clientId);
            if (clientConfig == null) {
                logger.warn("⚠️ No configuration found for client {}, using default limits", clientId);
                // Fallback to default limits
                clientConfig = createDefaultConfiguration(clientId);
            } else {
                logger.info("✅ Got configuration for {}: dailyRisk={}, maxRisk={}",
                    clientId,
                    clientConfig.getDailyRisk() != null ? clientConfig.getDailyRisk().getValue() : "null",
                    clientConfig.getMaxRisk() != null ? clientConfig.getMaxRisk().getValue() : "null");
            }
            
            // Calculate risk limits based on initial balance
            BigDecimal maxRiskLimit = calculateRiskThreshold(clientConfig.getMaxRisk(), monitoring.getInitialBalance());
            BigDecimal dailyRiskLimit = calculateRiskThreshold(clientConfig.getDailyRisk(), monitoring.getDailyStartBalance());
            
            BigDecimal currentLoss = monitoring.getCurrentLoss();
            BigDecimal dailyLoss = monitoring.getDailyLoss();

            logger.info("🔍 RISK CHECK DETAILS for client {}:", clientId);
            logger.info("  - Initial Balance: ${}", monitoring.getInitialBalance());
            logger.info("  - Daily Start Balance: ${}", monitoring.getDailyStartBalance());
            logger.info("  - Current Balance: ${}", monitoring.getCurrentBalance());
            logger.info("  - Current Loss: ${}", currentLoss);
            logger.info("  - Daily Loss: ${}", dailyLoss);
            logger.info("  - Max Risk Limit: ${} ({})", maxRiskLimit, clientConfig.getMaxRisk().getType());
            logger.info("  - Daily Risk Limit: ${} ({})", dailyRiskLimit, clientConfig.getDailyRisk().getType());
            
            // REQUIREMENT 3: Max Risk Trigger
            if (currentLoss.compareTo(maxRiskLimit) >= 0) {
                logger.error("🚨 MAX RISK TRIGGERED for client {}: loss=${}, limit=${}", 
                           clientId, currentLoss, maxRiskLimit);
                
                executeMaxRiskActions(monitoring, currentLoss, maxRiskLimit);
                return;
            }
            
            // REQUIREMENT 3: Daily Risk Trigger  
            if (dailyLoss.compareTo(dailyRiskLimit) >= 0) {
                logger.warn("⚠️ DAILY RISK TRIGGERED for client {}: loss=${}, limit=${}", 
                           clientId, dailyLoss, dailyRiskLimit);
                
                executeDailyRiskActions(monitoring, dailyLoss, dailyRiskLimit);
                return;
            }
            
            // No violations - update status to normal if needed
            if (monitoring.getRiskStatus() != RiskStatus.NORMAL) {
                monitoring.setRiskStatus(RiskStatus.NORMAL);
                accountMonitoringRepository.save(monitoring);
                logger.debug("✅ Risk status normalized for client {}", clientId);
            }
            
        } catch (Exception e) {
            logger.error("❌ Error checking risk limits for client {}: {}", 
                        monitoring.getClientId(), e.getMessage());
            
            // Send error notification
            publishNotificationEvent(NotificationEvent.monitoringError(
                monitoring.getClientId(), 
                "Error during risk limit check: " + e.getMessage()
            ));
        }
    }
    
    private void executeMaxRiskActions(AccountMonitoring monitoring, BigDecimal currentLoss, BigDecimal maxRiskLimit) {
        riskActionExecutor.executeRiskActions(monitoring, ViolationType.MAX_RISK, currentLoss, maxRiskLimit);
    }
    
    private void executeDailyRiskActions(AccountMonitoring monitoring, BigDecimal dailyLoss, BigDecimal dailyRiskLimit) {
        riskActionExecutor.executeRiskActions(monitoring, ViolationType.DAILY_RISK, dailyLoss, dailyRiskLimit);
    }

    // Method removed - duplicate of executeMaxRiskActions

    // Method removed - duplicate of executeDailyRiskActions

    /**
     * 📊 GET COMPREHENSIVE RISK STATUS
     */
    public Map<String, Object> getRiskStatus(String clientId) {
        try {
            Optional<AccountMonitoring> monitoring = accountMonitoringRepository.findByClientId(clientId);
            if (!monitoring.isPresent()) {
                return Map.of("error", "No monitoring data found for client: " + clientId);
            }
            
            AccountMonitoring account = monitoring.get();
            
            return Map.of(
                "clientId", clientId,
                "currentBalance", account.getCurrentBalance(),
                "initialBalance", account.getInitialBalance(),
                "dailyPnl", account.getDailyPnl(),
                "totalPnl", account.getCurrentBalance().subtract(account.getInitialBalance()),
                "isBlocked", account.isPermanentlyBlocked() || account.isDailyBlocked(),
                "lastUpdate", account.getUpdatedAt()
            );
            
        } catch (Exception e) {
            return Map.of("error", "Error retrieving status: " + e.getMessage());
        }
    }

    /**
     * 🔄 FORCE BALANCE UPDATE
     */
    public void forceBalanceUpdate(String clientId) {
        try {
            ClientConfiguration config = getClientConfiguration(clientId);
            if (config != null) {
                ArchitectBalanceResponse balance = architectApiService.getAccountBalance(
                    config.getApiKey(), config.getApiSecret()
                );
                
                if (balance != null && balance.getTotalBalance() != null) {
                    BalanceUpdateEvent event = new BalanceUpdateEvent();
                    event.setClientId(clientId);
                    event.setNewBalance(balance.getTotalBalance());
                    event.setSource("manual_update");
                    event.setTimestamp(LocalDateTime.now());
                    
                    processBalanceUpdate(event);
                }
            }
        } catch (Exception e) {
            logger.error("❌ Error forcing balance update: {}", e.getMessage());
        }
    }
}
