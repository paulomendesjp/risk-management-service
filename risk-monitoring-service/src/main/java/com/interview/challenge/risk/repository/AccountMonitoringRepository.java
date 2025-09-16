package com.interview.challenge.risk.repository;

import com.interview.challenge.risk.model.AccountMonitoring;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.Query;
import com.interview.challenge.risk.model.RiskStatus;

/**
 * 📊 ACCOUNT MONITORING REPOSITORY
 */
@Repository
public interface AccountMonitoringRepository extends MongoRepository<AccountMonitoring, String> {
    
    Optional<AccountMonitoring> findByClientId(String clientId);
    
    List<AccountMonitoring> findByDailyBlocked(boolean dailyBlocked);
    
    List<AccountMonitoring> findByPermanentlyBlocked(boolean permanentlyBlocked);
    
    List<AccountMonitoring> findByRealTimeMonitoring(boolean realTimeMonitoring);
    
    /**
     * Find accounts that need daily reset (new day started)
     */
    @Query("{'dailyStartBalance': {$lt: ?0}, 'dailyBlocked': true}")
    List<AccountMonitoring> findAccountsNeedingDailyReset(LocalDateTime cutoffTime);
    
    /**
     * Find accounts that need risk check
     */
    @Query("{'lastRiskCheck': {$lt: ?0}, 'realTimeMonitoring': true}")
    List<AccountMonitoring> findAccountsNeedingRiskCheck(LocalDateTime cutoffTime);
    
    /**
     * Count blocked accounts
     */
    @Query(value = "{}", count = true)
    long countBlockedAccounts();
    
    /**
     * Count accounts by risk status
     */
    long countByRiskStatus(RiskStatus riskStatus);
}
