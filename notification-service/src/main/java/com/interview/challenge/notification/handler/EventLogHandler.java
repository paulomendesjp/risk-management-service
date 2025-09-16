package com.interview.challenge.notification.handler;

import com.interview.challenge.shared.event.NotificationEvent;

/**
 * Interface for handling event logging
 */
@FunctionalInterface
public interface EventLogHandler {
    void handle(NotificationEvent event);
}