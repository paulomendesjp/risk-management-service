package com.interview.challenge.notification.channel;

import com.interview.challenge.shared.event.NotificationEvent;

/**
 * Interface for notification channels
 * Implements Strategy pattern for different notification methods
 */
public interface NotificationChannel {

    /**
     * Send notification through this channel
     * @param event The notification event to send
     * @return true if notification was sent successfully, false otherwise
     */
    boolean send(NotificationEvent event);

    /**
     * Check if this channel is enabled
     * @return true if channel is enabled, false otherwise
     */
    boolean isEnabled();

    /**
     * Get the channel name
     * @return channel name
     */
    String getChannelName();

    /**
     * Determine if this channel should handle the given event
     * @param event The notification event
     * @return true if this channel should handle the event
     */
    boolean shouldHandle(NotificationEvent event);
}