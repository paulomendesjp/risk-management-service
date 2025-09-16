package com.interview.challenge.user.repository;

import com.interview.challenge.shared.model.ClientConfiguration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * User Repository for MongoDB operations
 * Handles CRUD operations for ClientConfiguration entities
 */
@Repository
public interface UserRepository extends MongoRepository<ClientConfiguration, String> {

    /**
     * Find user by client ID
     */
    Optional<ClientConfiguration> findByClientId(String clientId);

    /**
     * Check if user exists by client ID
     */
    boolean existsByClientId(String clientId);

    /**
     * Find all active users (not blocked)
     */
    @Query("{'dailyBlocked': false, 'permanentBlocked': false}")
    List<ClientConfiguration> findAllActiveUsers();

    /**
     * Find users blocked today
     */
    @Query("{'dailyBlocked': true}")
    List<ClientConfiguration> findDailyBlockedUsers();

    /**
     * Find permanently blocked users
     */
    @Query("{'permanentBlocked': true}")
    List<ClientConfiguration> findPermanentlyBlockedUsers();

    /**
     * Find users by risk limit type
     */
    @Query("{'maxRisk.type': ?0}")
    List<ClientConfiguration> findByMaxRiskType(String type);

    /**
     * Find users with daily risk limit above threshold
     */
    @Query("{'dailyRisk.value': {$gte: ?0}}")
    List<ClientConfiguration> findByDailyRiskValueGreaterThanEqual(Double value);

    /**
     * Count active users
     */
    @Query(value = "{'dailyBlocked': false, 'permanentBlocked': false}", count = true)
    long countActiveUsers();

    /**
     * Delete user by client ID
     */
    void deleteByClientId(String clientId);
}


