package com.interview.challenge.notification.handler;

import com.interview.challenge.notification.enums.NotificationEventType;
import com.interview.challenge.shared.audit.MandatoryAuditLogger;
import com.interview.challenge.shared.event.NotificationEvent;

/**
 * Abstract base class for notification event handlers
 * Implements Strategy pattern for handling different event types
 */
public abstract class NotificationEventHandler {

    protected final MandatoryAuditLogger auditLogger;

    protected NotificationEventHandler(MandatoryAuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    /**
     * Handle the notification event
     * @param event The event to handle
     */
    public abstract void handle(NotificationEvent event);

    /**
     * Determine if this handler can process the given event type
     * @param eventType The event type
     * @return true if this handler can process the event
     */
    public abstract boolean canHandle(String eventType);

    /**
     * Get the supported event type for this handler
     * @return The event type this handler supports
     */
    public abstract NotificationEventType getSupportedEventType();
}