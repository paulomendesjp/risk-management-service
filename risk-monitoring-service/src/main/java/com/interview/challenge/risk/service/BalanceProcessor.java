package com.interview.challenge.risk.service;

import com.interview.challenge.risk.constants.RiskConstants;
import com.interview.challenge.risk.model.AccountMonitoring;
import com.interview.challenge.risk.repository.AccountMonitoringRepository;
import com.interview.challenge.shared.dto.ArchitectBalanceResponse;
import com.interview.challenge.shared.event.BalanceUpdateEvent;
import com.interview.challenge.shared.service.ArchitectApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class BalanceProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BalanceProcessor.class);

    @Autowired
    private AccountMonitoringRepository accountMonitoringRepository;

    @Autowired
    private ArchitectApiService architectApiService;

    /**
     * Process balance update from external source
     */
    public AccountMonitoring processBalanceUpdate(BalanceUpdateEvent event) {
        String clientId = event.getClientId();
        BigDecimal newBalance = event.getNewBalance();

        logger.info("Processing balance update for client {}: ${}", clientId, newBalance);

        // Get or initialize monitoring
        AccountMonitoring monitoring = getOrInitializeMonitoring(clientId, newBalance);

        // Update balance
        BigDecimal previousBalance = monitoring.getCurrentBalance();
        monitoring.updateBalance(newBalance, BigDecimal.ZERO);

        // Save changes
        monitoring = accountMonitoringRepository.save(monitoring);

        logger.debug("Balance updated for client {}: ${} -> ${}",
            clientId, previousBalance, newBalance);

        return monitoring;
    }

    /**
     * Fetch and update balance from Architect API
     */
    public AccountMonitoring fetchAndUpdateBalance(String clientId, String apiKey, String apiSecret) {
        try {
            logger.debug("Fetching balance from Architect API for client: {}", clientId);

            // Get balance from API
            ArchitectBalanceResponse balanceResponse = architectApiService.getAccountBalance(apiKey, apiSecret);
            BigDecimal currentBalance = balanceResponse.getTotalBalance();
            BigDecimal unrealizedPnl = balanceResponse.getRealizedPnl();

            // Create balance update event
            BalanceUpdateEvent event = new BalanceUpdateEvent();
            event.setClientId(clientId);
            event.setNewBalance(currentBalance);
            event.setSource(RiskConstants.SOURCE_RISK_MONITORING);
            event.setTimestamp(LocalDateTime.now());

            // Process the update
            return processBalanceUpdate(event);

        } catch (Exception e) {
            logger.error("Error fetching balance for client {}: {}", clientId, e.getMessage());
            throw new RuntimeException("Failed to fetch balance: " + e.getMessage(), e);
        }
    }

    /**
     * Get or initialize account monitoring
     */
    private AccountMonitoring getOrInitializeMonitoring(String clientId, BigDecimal initialBalance) {
        Optional<AccountMonitoring> existing = accountMonitoringRepository.findByClientId(clientId);

        if (existing.isPresent()) {
            return existing.get();
        }

        logger.info("Initializing monitoring for new client: {} with balance: ${}", clientId, initialBalance);
        AccountMonitoring monitoring = new AccountMonitoring(clientId, initialBalance);
        return accountMonitoringRepository.save(monitoring);
    }

    /**
     * Calculate PnL (Profit and Loss)
     */
    public BigDecimal calculatePnL(BigDecimal currentBalance, BigDecimal initialBalance) {
        if (currentBalance == null || initialBalance == null) {
            return BigDecimal.ZERO;
        }
        return currentBalance.subtract(initialBalance);
    }

    /**
     * Calculate daily PnL
     */
    public BigDecimal calculateDailyPnL(BigDecimal currentBalance, BigDecimal dailyStartBalance) {
        if (currentBalance == null || dailyStartBalance == null) {
            return BigDecimal.ZERO;
        }
        return currentBalance.subtract(dailyStartBalance);
    }

    /**
     * Check if balance has changed significantly (more than $100)
     */
    public boolean isSignificantChange(BigDecimal oldBalance, BigDecimal newBalance) {
        if (oldBalance == null || newBalance == null) {
            return true;
        }
        BigDecimal change = newBalance.subtract(oldBalance).abs();
        return change.compareTo(new BigDecimal("100")) > 0;
    }
}