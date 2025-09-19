package com.interview.challenge.kraken.repository;

import com.interview.challenge.kraken.model.KrakenAccount;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Kraken Account data
 */
@Repository
public interface KrakenAccountRepository extends MongoRepository<KrakenAccount, String> {

    Optional<KrakenAccount> findByClientId(String clientId);

    List<KrakenAccount> findByStatus(String status);

    List<KrakenAccount> findByTradingEnabled(Boolean tradingEnabled);

    @Query("{'dailyBlocked': true, 'dailyResetTime': {$lte: ?0}}")
    List<KrakenAccount> findAccountsForDailyReset(LocalDateTime resetTime);

    @Query("{'status': 'ACTIVE', 'lastBalanceCheck': {$lte: ?0}}")
    List<KrakenAccount> findAccountsNeedingBalanceCheck(LocalDateTime checkTime);

    Long countByStatus(String status);

    Long countByDailyBlocked(Boolean dailyBlocked);

    @Query("{'riskViolationCount': {$gt: 0}}")
    List<KrakenAccount> findAccountsWithRiskViolations();
}