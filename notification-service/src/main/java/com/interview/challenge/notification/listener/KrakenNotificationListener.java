package com.interview.challenge.notification.listener;

import com.interview.challenge.shared.event.BalanceUpdateEvent;
import com.interview.challenge.shared.event.NotificationEvent;
import com.interview.challenge.notification.service.NotificationOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Specific listener for Kraken-related notifications
 * Handles events published by Kraken Service
 */
@Component
public class KrakenNotificationListener {

    private static final Logger logger = LoggerFactory.getLogger(KrakenNotificationListener.class);

    @Autowired
    private NotificationOrchestrator notificationOrchestrator;

    /**
     * Listen to balance update events from Kraken service
     */
    @RabbitListener(queues = "balance.update.queue")
    public void handleBalanceUpdate(BalanceUpdateEvent event) {
        try {
            logger.info("🦑 Received Kraken balance update for client: {}", event.getClientId());

            // Convert to NotificationEvent for processing
            NotificationEvent notificationEvent = NotificationEvent.balanceUpdate(
                event.getClientId(),
                event.getNewBalance(),
                event.getPreviousBalance(),
                "KRAKEN"
            );

            // Set exchange field to identify source
            notificationEvent.setExchange("KRAKEN");

            // Process through main orchestrator
            notificationOrchestrator.handleNotificationEvent(notificationEvent);

        } catch (Exception e) {
            logger.error("❌ Error processing Kraken balance update: {}", e.getMessage(), e);
        }
    }

    /**
     * Listen to general notification events from Kraken service
     */
    @RabbitListener(queues = "notification.queue")
    public void handleKrakenNotification(NotificationEvent event) {
        try {
            logger.info("🦑 Received Kraken notification event: {} for client {}",
                       event.getEventType(), event.getClientId());

            // Set exchange field to identify source
            event.setExchange("KRAKEN");

            // Process through main orchestrator
            notificationOrchestrator.handleNotificationEvent(event);

        } catch (Exception e) {
            logger.error("❌ Error processing Kraken notification: {}", e.getMessage(), e);
        }
    }
}