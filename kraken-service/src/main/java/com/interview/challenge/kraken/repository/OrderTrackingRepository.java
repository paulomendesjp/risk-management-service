package com.interview.challenge.kraken.repository;

import com.interview.challenge.kraken.model.OrderTracking;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Order Tracking
 */
@Repository
public interface OrderTrackingRepository extends MongoRepository<OrderTracking, String> {

    Optional<OrderTracking> findByOrderId(String orderId);

    List<OrderTracking> findByClientId(String clientId);

    List<OrderTracking> findByClientIdAndStatus(String clientId, String status);

    @Query("{'clientId': ?0, 'strategy': ?1, 'status': 'ACTIVE'}")
    List<OrderTracking> findActiveOrdersByStrategy(String clientId, String strategy);

    @Query("{'clientId': ?0, 'symbol': ?1, 'status': 'ACTIVE'}")
    List<OrderTracking> findActiveOrdersBySymbol(String clientId, String symbol);

    @Query("{'clientId': ?0, 'createdAt': {$gte: ?1, $lte: ?2}}")
    List<OrderTracking> findOrdersInTimeRange(String clientId, LocalDateTime start, LocalDateTime end);

    @Query(value = "{'clientId': ?0, 'status': 'ACTIVE'}")
    @Update("{'$set': {'status': 'CLOSED', 'updatedAt': ?1}}")
    void markAllClosedForClient(String clientId, LocalDateTime closedAt);

    default void markAllClosedForClient(String clientId) {
        markAllClosedForClient(clientId, LocalDateTime.now());
    }

    Long countByClientIdAndStatus(String clientId, String status);

    @Query("{'strategy': ?0, 'status': 'ACTIVE', 'pyramid': true}")
    Long countPyramidOrdersByStrategy(String strategy);
}