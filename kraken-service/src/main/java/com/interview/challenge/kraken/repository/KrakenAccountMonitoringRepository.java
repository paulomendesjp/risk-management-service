package com.interview.challenge.kraken.repository;

import com.interview.challenge.kraken.model.KrakenAccountMonitoring;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Kraken account monitoring data
 */
@Repository
public interface KrakenAccountMonitoringRepository extends MongoRepository<KrakenAccountMonitoring, String> {

    /**
     * Find monitoring by client ID
     */
    Optional<KrakenAccountMonitoring> findByClientId(String clientId);

    /**
     * Check if monitoring exists for client
     */
    boolean existsByClientId(String clientId);

    /**
     * Find all active monitoring accounts
     */
    List<KrakenAccountMonitoring> findByActiveTrue();

    /**
     * Find accounts that can trade (not blocked)
     */
    @Query("{ 'active': true, 'dailyBlocked': false, 'permanentBlocked': false }")
    List<KrakenAccountMonitoring> findAccountsCanTrade();

    /**
     * Find accounts blocked by daily risk
     */
    List<KrakenAccountMonitoring> findByDailyBlockedTrue();

    /**
     * Find accounts blocked permanently
     */
    List<KrakenAccountMonitoring> findByPermanentBlockedTrue();

    /**
     * Find accounts needing daily reset
     */
    @Query("{ 'dailyBlocked': true, 'dailyBlockedAt': { $lt: ?0 } }")
    List<KrakenAccountMonitoring> findAccountsNeedingReset(LocalDateTime cutoffTime);

    /**
     * Find accounts not updated recently
     */
    @Query("{ 'active': true, 'lastBalanceUpdate': { $lt: ?0 } }")
    List<KrakenAccountMonitoring> findStaleAccounts(LocalDateTime threshold);

    /**
     * Count blocked accounts
     */
    @Query("{ $or: [ { 'dailyBlocked': true }, { 'permanentBlocked': true } ] }")
    long countBlockedAccounts();

    /**
     * Find accounts with risk violations
     */
    @Query("{ $or: [ { 'dailyPnl': { $lt: 0 } }, { 'totalPnl': { $lt: 0 } } ] }")
    List<KrakenAccountMonitoring> findAccountsWithLosses();

    /**
     * Delete monitoring by client ID
     */
    void deleteByClientId(String clientId);
}