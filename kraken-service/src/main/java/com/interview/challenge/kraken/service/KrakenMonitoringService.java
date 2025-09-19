package com.interview.challenge.kraken.service;

import com.interview.challenge.kraken.client.KrakenApiClient;
import com.interview.challenge.kraken.client.KrakenAuthenticator;
import com.interview.challenge.kraken.dto.KrakenBalanceResponse;
import com.interview.challenge.kraken.model.KrakenAccountMonitoring;
import com.interview.challenge.kraken.model.KrakenAccountMonitoring.RiskLimit;
import com.interview.challenge.kraken.repository.KrakenAccountMonitoringRepository;
import com.interview.challenge.kraken.service.KrakenRiskCalculator.RiskCheckResult;
import com.interview.challenge.shared.event.NotificationEvent;
import com.interview.challenge.shared.event.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for monitoring Kraken account balances in real-time
 * Implements requirement: "The service should continuously monitor user's account balance in real-time"
 * Handles risk management, position closure, and account blocking
 */
@Slf4j
@Service
public class KrakenMonitoringService {

    @Autowired
    private KrakenApiClient krakenApiClient;

    @Autowired
    private KrakenAccountMonitoringRepository repository;

    @Autowired
    private KrakenRiskCalculator riskCalculator;

    @Autowired
    private KrakenTradingService tradingService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private KrakenAuthenticator krakenAuthenticator;

    @Autowired
    private KrakenCredentialManager credentialManager;

    @Autowired(required = false)
    private com.interview.challenge.kraken.websocket.KrakenWebSocketClient webSocketClient;

    @Value("${kraken.monitoring.interval:10000}")
    private int monitoringInterval;

    @Value("${kraken.monitoring.mode:websocket}")
    private String monitoringMode; // "websocket" or "polling"

    /**
     * Start monitoring a Kraken account
     */
    public void startMonitoring(String clientId, String apiKey, String apiSecret, BigDecimal initialBalance, RiskLimit dailyRisk, RiskLimit maxRisk) {
        log.info("🦑 Starting Kraken balance monitoring for client: {}", clientId);

        // Validate credentials first
        if (!krakenAuthenticator.validateCredentials(apiKey, apiSecret)) {
            log.error("❌ Invalid Kraken API credentials for client: {}", clientId);
            throw new IllegalArgumentException("Invalid Kraken API credentials");
        }

        // Encrypt credentials before storing
        String encryptedApiKey = credentialManager.encryptCredential(apiKey);
        String encryptedApiSecret = credentialManager.encryptCredential(apiSecret);

        // Check if already monitoring
        if (repository.existsByClientId(clientId)) {
            log.warn("Already monitoring client: {}", clientId);
            KrakenAccountMonitoring existing = repository.findByClientId(clientId).orElse(null);
            if (existing != null) {
                existing.setActive(true);
                existing.setApiKey(encryptedApiKey);
                existing.setApiSecret(encryptedApiSecret);
                repository.save(existing);
            }
            return;
        }

        // Create new monitoring
        KrakenAccountMonitoring monitoring = KrakenAccountMonitoring.builder()
            .clientId(clientId)
            .apiKey(encryptedApiKey)
            .apiSecret(encryptedApiSecret)
            .initialBalance(initialBalance)
            .currentBalance(initialBalance)
            .dailyStartBalance(initialBalance)
            .dailyRisk(dailyRisk)
            .maxRisk(maxRisk)
            .active(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .dailyPnl(BigDecimal.ZERO)
            .totalPnl(BigDecimal.ZERO)
            .build();

        repository.save(monitoring);

        log.info("✅ Started monitoring for Kraken client: {} with initial balance: ${} (mode: {})",
            clientId, initialBalance, monitoringMode);

        // Start monitoring based on mode
        if ("websocket".equalsIgnoreCase(monitoringMode) && webSocketClient != null) {
            // Use WebSocket for real-time monitoring
            log.info("🔌 Starting WebSocket monitoring for client: {}", clientId);
            webSocketClient.subscribeToBalances(clientId, apiKey, apiSecret);
        } else {
            // Fall back to polling
            log.info("⏰ Using polling mode for client: {}", clientId);
            // Fetch initial balance
            fetchAndUpdateBalance(clientId);
        }
    }

    /**
     * Check if monitoring a specific client
     */
    public boolean isMonitoring(String clientId) {
        return repository.existsByClientId(clientId);
    }

    /**
     * Update risk limits for a monitored account
     */
    public void updateRiskLimits(String clientId, RiskLimit dailyRisk, RiskLimit maxRisk) {
        repository.findByClientId(clientId).ifPresent(monitoring -> {
            if (dailyRisk != null) {
                monitoring.setDailyRisk(dailyRisk);
            }
            if (maxRisk != null) {
                monitoring.setMaxRisk(maxRisk);
            }
            monitoring.setUpdatedAt(LocalDateTime.now());
            repository.save(monitoring);

            log.info("📊 Updated risk limits for client {}: Daily={}, Max={}",
                clientId, dailyRisk, maxRisk);
        });
    }

    /**
     * Stop monitoring a Kraken account
     */
    public void stopMonitoring(String clientId) {
        log.info("🛑 Stopping Kraken balance monitoring for client: {}", clientId);

        repository.findByClientId(clientId).ifPresent(monitoring -> {
            monitoring.setActive(false);
            monitoring.setUpdatedAt(LocalDateTime.now());
            repository.save(monitoring);

            // Unsubscribe from WebSocket if using WebSocket mode
            if ("websocket".equalsIgnoreCase(monitoringMode) && webSocketClient != null) {
                log.info("🔌 Unsubscribing WebSocket for client: {}", clientId);
                webSocketClient.unsubscribeFromBalances(clientId);
            }
        });
    }

    /**
     * Poll Kraken balances every 10 seconds (configurable)
     * This implements real-time monitoring requirement
     * NOTE: Only runs when monitoring.mode=polling
     */
    @Scheduled(fixedDelayString = "${kraken.monitoring.interval:10000}")
    public void pollKrakenBalances() {
        // Skip if using WebSocket mode
        if ("websocket".equalsIgnoreCase(monitoringMode)) {
            return;
        }

        List<KrakenAccountMonitoring> activeAccounts = repository.findByActiveTrue();

        if (activeAccounts.isEmpty()) {
            return;
        }

        log.debug("📊 Polling Kraken balances for {} accounts", activeAccounts.size());

        for (KrakenAccountMonitoring account : activeAccounts) {
            if (account.canTrade() || account.isDailyBlocked()) {
                fetchAndUpdateBalance(account.getClientId());
            }
        }
    }

    /**
     * Fetch balance from Kraken API and check risk
     */
    private void fetchAndUpdateBalance(String clientId) {
        repository.findByClientId(clientId).ifPresent(account -> {
            if (!account.isActive()) {
                return;
            }

            try {
                // Decrypt credentials before using them
                String apiKey = credentialManager.decryptCredential(account.getApiKey());
                String apiSecret = credentialManager.decryptCredential(account.getApiSecret());

                // Fetch current balance from Kraken
                KrakenBalanceResponse balanceResponse = krakenApiClient.getAccountBalance(
                    apiKey,
                    apiSecret
                );

                if (balanceResponse != null && balanceResponse.getResult() != null) {
                    BigDecimal currentBalance = extractTotalBalance(balanceResponse);
                    BigDecimal previousBalance = account.getCurrentBalance();

                    // Update balance and P&L
                    account.updateBalance(currentBalance);
                    account.setLastChecked(LocalDateTime.now());

                    // Check if balance changed
                    if (previousBalance == null || currentBalance.compareTo(previousBalance) != 0) {
                        log.info("💰 Balance change detected for Kraken client {}: ${} -> ${}",
                            clientId, previousBalance, currentBalance);
                    }

                    // Check risk limits
                    checkAndHandleRisk(account);

                    // Save updated account
                    repository.save(account);
                }

            } catch (Exception e) {
                log.error("❌ Error fetching Kraken balance for client {}: {}", clientId, e.getMessage());
                publishMonitoringError(clientId, e.getMessage());
            }
        });
    }

    /**
     * Check risk limits and take action if violated
     */
    private void checkAndHandleRisk(KrakenAccountMonitoring account) {
        if (account.isPermanentBlocked()) {
            // Already permanently blocked, skip
            return;
        }

        RiskCheckResult riskResult = riskCalculator.checkRisk(account);

        if (riskResult.hasViolation()) {
            log.warn("⚠️ Risk violation detected for client {}: {}",
                account.getClientId(), riskResult.getStatus());

            if (riskResult.isMaxRiskViolated()) {
                handleMaxRiskViolation(account, riskResult);
            } else if (riskResult.isDailyRiskViolated() && !account.isDailyBlocked()) {
                handleDailyRiskViolation(account, riskResult);
            }
        } else if (riskResult.isWarning()) {
            publishRiskWarning(account, riskResult);
        }
    }

    /**
     * Handle max risk violation - close positions and block permanently
     */
    private void handleMaxRiskViolation(KrakenAccountMonitoring account, RiskCheckResult riskResult) {
        String clientId = account.getClientId();
        log.error("🚨 MAX RISK VIOLATED for Kraken client {}: Loss ${} >= Limit ${}",
            clientId, riskResult.getMaxRiskLoss(), riskResult.getMaxRiskThreshold());

        // 1. Close all positions
        Map<String, Object> closeResult = closeAllPositions(account);

        // 2. Block account permanently
        String reason = String.format("MAX RISK LIMIT EXCEEDED - Loss: $%.2f, Limit: $%.2f",
            riskResult.getMaxRiskLoss(), riskResult.getMaxRiskThreshold());
        account.blockPermanently(reason);
        repository.save(account);

        // 3. Publish notification
        publishRiskViolationNotification(
            clientId,
            "MAX_RISK",
            riskResult.getMaxRiskLoss(),
            riskResult.getMaxRiskThreshold(),
            closeResult
        );

        log.error("🔒 Account {} permanently blocked due to max risk violation", clientId);
    }

    /**
     * Handle daily risk violation - close positions and block until tomorrow
     */
    private void handleDailyRiskViolation(KrakenAccountMonitoring account, RiskCheckResult riskResult) {
        String clientId = account.getClientId();
        log.warn("⚠️ DAILY RISK VIOLATED for Kraken client {}: Loss ${} >= Limit ${}",
            clientId, riskResult.getDailyRiskLoss(), riskResult.getDailyRiskThreshold());

        // 1. Close all positions
        Map<String, Object> closeResult = closeAllPositions(account);

        // 2. Block account for the day
        String reason = String.format("DAILY RISK LIMIT EXCEEDED - Loss: $%.2f, Limit: $%.2f",
            riskResult.getDailyRiskLoss(), riskResult.getDailyRiskThreshold());
        account.blockDaily(reason);
        repository.save(account);

        // 3. Publish notification
        publishRiskViolationNotification(
            clientId,
            "DAILY_RISK",
            riskResult.getDailyRiskLoss(),
            riskResult.getDailyRiskThreshold(),
            closeResult
        );

        log.warn("🔒 Account {} blocked until 00:01 UTC due to daily risk violation", clientId);
    }

    /**
     * Close all positions for an account
     */
    private Map<String, Object> closeAllPositions(KrakenAccountMonitoring account) {
        try {
            log.info("📉 Closing all positions for Kraken client: {}", account.getClientId());

            // Decrypt credentials before using them
            String apiKey = credentialManager.decryptCredential(account.getApiKey());
            String apiSecret = credentialManager.decryptCredential(account.getApiSecret());

            return tradingService.closeAllPositions(
                account.getClientId(),
                apiKey,
                apiSecret
            );

        } catch (Exception e) {
            log.error("❌ Failed to close positions for {}: {}", account.getClientId(), e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    /**
     * Reset daily blocks at 00:01 UTC
     */
    @Scheduled(cron = "0 1 0 * * ?", zone = "UTC")
    public void resetDailyBlocks() {
        log.info("🔄 Starting daily reset for Kraken accounts at 00:01 UTC");

        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        List<KrakenAccountMonitoring> blockedAccounts = repository.findAccountsNeedingReset(cutoff);

        for (KrakenAccountMonitoring account : blockedAccounts) {
            try {
                account.resetDaily();
                repository.save(account);

                log.info("✅ Daily reset completed for Kraken client: {}", account.getClientId());

                // Publish reset notification
                publishDailyResetNotification(account.getClientId());

            } catch (Exception e) {
                log.error("❌ Error resetting daily block for {}: {}",
                    account.getClientId(), e.getMessage());
            }
        }

        log.info("✅ Daily reset completed for {} Kraken accounts", blockedAccounts.size());
    }

    /**
     * Extract total balance from Kraken response
     */
    private BigDecimal extractTotalBalance(KrakenBalanceResponse response) {
        BigDecimal total = BigDecimal.ZERO;

        if (response.getResult() != null) {
            // For futures, use account equity or portfolio value
            KrakenBalanceResponse.AccountBalance mainAccount = response.getMainAccount();
            if (mainAccount != null) {
                if (mainAccount.getPortfolioValue() != null) {
                    total = mainAccount.getPortfolioValue();
                } else if (mainAccount.getMarginBalance() != null) {
                    total = mainAccount.getMarginBalance();
                } else if (mainAccount.getBalance() != null) {
                    total = mainAccount.getBalance();
                }
            }
        }

        return total;
    }

    /**
     * Get monitoring status for a client
     */
    public Map<String, Object> getMonitoringStatus(String clientId) {
        Map<String, Object> status = new HashMap<>();

        repository.findByClientId(clientId).ifPresentOrElse(
            account -> {
                status.put("active", account.isActive());
                status.put("clientId", account.getClientId());
                status.put("currentBalance", account.getCurrentBalance());
                status.put("initialBalance", account.getInitialBalance());
                status.put("dailyPnl", account.getDailyPnl());
                status.put("totalPnl", account.getTotalPnl());
                status.put("canTrade", account.canTrade());
                status.put("dailyBlocked", account.isDailyBlocked());
                status.put("permanentBlocked", account.isPermanentBlocked());
                status.put("lastChecked", account.getLastChecked());
            },
            () -> {
                status.put("active", false);
                status.put("error", "Account not found");
            }
        );

        return status;
    }

    // ===== NOTIFICATION METHODS =====

    /**
     * Publish risk violation notification
     */
    private void publishRiskViolationNotification(String clientId, String type,
                                                 BigDecimal loss, BigDecimal limit,
                                                 Map<String, Object> closeResult) {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("exchange", "KRAKEN");
            details.put("violationType", type);
            details.put("loss", loss);
            details.put("limit", limit);
            details.put("positionsClosed", closeResult.get("positionsClosed"));
            details.put("timestamp", LocalDateTime.now());

            NotificationEvent event = NotificationEvent.builder()
                .clientId(clientId)
                .eventType(NotificationType.RISK_VIOLATION)
                .details(details)
                .build();

            rabbitTemplate.convertAndSend("notifications", event);

            log.info("📨 Published {} notification for Kraken client {}", type, clientId);

        } catch (Exception e) {
            log.error("❌ Failed to publish notification: {}", e.getMessage());
        }
    }

    /**
     * Publish risk warning notification
     */
    private void publishRiskWarning(KrakenAccountMonitoring account, RiskCheckResult riskResult) {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("exchange", "KRAKEN");
            details.put("warningType", "RISK_WARNING");
            details.put("dailyRiskPercentage", riskResult.getDailyRiskPercentage());
            details.put("maxRiskPercentage", riskResult.getMaxRiskPercentage());

            NotificationEvent event = NotificationEvent.builder()
                .clientId(account.getClientId())
                .eventType(NotificationType.RISK_WARNING)
                .details(details)
                .build();

            rabbitTemplate.convertAndSend("notifications", event);

        } catch (Exception e) {
            log.error("❌ Failed to publish warning: {}", e.getMessage());
        }
    }

    /**
     * Publish monitoring error notification
     */
    private void publishMonitoringError(String clientId, String error) {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("exchange", "KRAKEN");
            details.put("error", error);

            NotificationEvent event = NotificationEvent.builder()
                .clientId(clientId)
                .eventType(NotificationType.MONITORING_ERROR)
                .details(details)
                .build();

            rabbitTemplate.convertAndSend("notifications", event);

        } catch (Exception e) {
            log.error("❌ Failed to publish error notification: {}", e.getMessage());
        }
    }

    /**
     * Publish daily reset notification
     */
    private void publishDailyResetNotification(String clientId) {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("exchange", "KRAKEN");
            details.put("message", "Daily risk limit reset - Trading enabled");

            NotificationEvent event = NotificationEvent.builder()
                .clientId(clientId)
                .eventType(NotificationType.DAILY_RESET)
                .details(details)
                .build();

            rabbitTemplate.convertAndSend("notifications", event);

        } catch (Exception e) {
            log.error("❌ Failed to publish reset notification: {}", e.getMessage());
        }
    }
}