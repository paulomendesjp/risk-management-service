package com.interview.challenge.notification.template;

import com.interview.challenge.shared.enums.NotificationEventType;
import com.interview.challenge.shared.enums.NotificationPriority;
import com.interview.challenge.shared.event.NotificationEvent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Service for managing notification templates
 * Provides structured templates for different notification types
 */
@Service
public class NotificationTemplateService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final NumberFormat CURRENCY_FORMATTER = NumberFormat.getCurrencyInstance(Locale.US);

    private final Map<NotificationEventType, NotificationTemplate> templates;

    public NotificationTemplateService() {
        this.templates = initializeTemplates();
    }

    /**
     * Get formatted message for notification event
     */
    public String getFormattedMessage(NotificationEvent event) {
        NotificationEventType eventType = event.getEventType();
        NotificationTemplate template = templates.get(eventType);

        if (template == null) {
            return getDefaultMessage(event);
        }

        return template.format(event);
    }

    /**
     * Get email subject for notification event
     */
    public String getEmailSubject(NotificationEvent event) {
        NotificationEventType eventType = event.getEventType();
        NotificationTemplate template = templates.get(eventType);

        if (template == null) {
            return String.format("[%s] %s", event.getPriority(), event.getEventType());
        }

        return template.getSubject(event);
    }

    /**
     * Get slack message for notification event
     */
    public String getSlackMessage(NotificationEvent event) {
        NotificationEventType eventType = event.getEventType();
        NotificationTemplate template = templates.get(eventType);

        if (template == null) {
            return getDefaultMessage(event);
        }

        return template.formatSlack(event);
    }

    private Map<NotificationEventType, NotificationTemplate> initializeTemplates() {
        Map<NotificationEventType, NotificationTemplate> templates = new HashMap<>();

        templates.put(NotificationEventType.MAX_RISK_TRIGGERED, new MaxRiskTemplate());
        templates.put(NotificationEventType.DAILY_RISK_TRIGGERED, new DailyRiskTemplate());
        templates.put(NotificationEventType.BALANCE_UPDATE, new BalanceUpdateTemplate());
        templates.put(NotificationEventType.POSITION_CLOSED, new PositionClosedTemplate());
        templates.put(NotificationEventType.ACCOUNT_BLOCKED, new AccountBlockedTemplate());
        templates.put(NotificationEventType.MONITORING_ERROR, new MonitoringErrorTemplate());
        templates.put(NotificationEventType.SYSTEM_EVENT, new SystemEventTemplate());

        return templates;
    }

    private String getDefaultMessage(NotificationEvent event) {
        return String.format("[%s] %s - Client: %s - %s",
            event.getPriority(),
            event.getEventType(),
            event.getClientId(),
            event.getMessage()
        );
    }

    private String formatCurrency(BigDecimal amount) {
        return amount != null ? CURRENCY_FORMATTER.format(amount) : "$0.00";
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_FORMATTER) : "";
    }

    /**
     * Base interface for notification templates
     */
    private interface NotificationTemplate {
        String format(NotificationEvent event);
        String getSubject(NotificationEvent event);
        String formatSlack(NotificationEvent event);
    }

    /**
     * Template for MAX_RISK_TRIGGERED events
     */
    private class MaxRiskTemplate implements NotificationTemplate {
        @Override
        public String format(NotificationEvent event) {
            return String.format(
                "CRITICAL: Maximum Risk Limit Exceeded\n\n" +
                "Client: %s\n" +
                "Loss Amount: %s\n" +
                "Risk Limit: %s\n" +
                "Timestamp: %s\n\n" +
                "Action: All positions closed. Trading permanently disabled.\n\n" +
                "This is a critical alert requiring immediate attention.",
                event.getClientId(),
                formatCurrency(event.getLoss()),
                formatCurrency(event.getLimit()),
                formatDateTime(event.getTimestamp())
            );
        }

        @Override
        public String getSubject(NotificationEvent event) {
            return String.format("[CRITICAL] Max Risk Triggered - Client: %s", event.getClientId());
        }

        @Override
        public String formatSlack(NotificationEvent event) {
            return String.format(
                ":rotating_light: *MAX RISK TRIGGERED*\n" +
                "```Client: %s\nLoss: %s (Limit: %s)\nAction: All positions closed, trading permanently disabled```",
                event.getClientId(),
                formatCurrency(event.getLoss()),
                formatCurrency(event.getLimit())
            );
        }
    }

    /**
     * Template for DAILY_RISK_TRIGGERED events
     */
    private class DailyRiskTemplate implements NotificationTemplate {
        @Override
        public String format(NotificationEvent event) {
            return String.format(
                "WARNING: Daily Risk Limit Exceeded\n\n" +
                "Client: %s\n" +
                "Daily Loss: %s\n" +
                "Daily Limit: %s\n" +
                "Timestamp: %s\n\n" +
                "Action: All positions closed. Trading disabled for today.\n",
                event.getClientId(),
                formatCurrency(event.getLoss()),
                formatCurrency(event.getLimit()),
                formatDateTime(event.getTimestamp())
            );
        }

        @Override
        public String getSubject(NotificationEvent event) {
            return String.format("[WARNING] Daily Risk Triggered - Client: %s", event.getClientId());
        }

        @Override
        public String formatSlack(NotificationEvent event) {
            return String.format(
                ":warning: *DAILY RISK TRIGGERED*\n" +
                "```Client: %s\nDaily Loss: %s (Limit: %s)\nAction: Positions closed, trading disabled today```",
                event.getClientId(),
                formatCurrency(event.getLoss()),
                formatCurrency(event.getLimit())
            );
        }
    }

    /**
     * Template for BALANCE_UPDATE events
     */
    private class BalanceUpdateTemplate implements NotificationTemplate {
        @Override
        public String format(NotificationEvent event) {
            return String.format(
                "Balance Update\n\n" +
                "Client: %s\n" +
                "Previous Balance: %s\n" +
                "New Balance: %s\n" +
                "Change: %s\n" +
                "Source: %s\n" +
                "Timestamp: %s",
                event.getClientId(),
                formatCurrency(event.getPreviousBalance()),
                formatCurrency(event.getNewBalance()),
                formatCurrency(event.getNewBalance().subtract(event.getPreviousBalance())),
                event.getSource(),
                formatDateTime(event.getTimestamp())
            );
        }

        @Override
        public String getSubject(NotificationEvent event) {
            return String.format("Balance Update - Client: %s", event.getClientId());
        }

        @Override
        public String formatSlack(NotificationEvent event) {
            BigDecimal change = event.getNewBalance().subtract(event.getPreviousBalance());
            String emoji = change.compareTo(BigDecimal.ZERO) > 0 ? ":chart_with_upwards_trend:" : ":chart_with_downwards_trend:";
            return String.format(
                "%s Balance: %s -> %s (Change: %s)",
                emoji,
                formatCurrency(event.getPreviousBalance()),
                formatCurrency(event.getNewBalance()),
                formatCurrency(change)
            );
        }
    }

    /**
     * Template for POSITION_CLOSED events
     */
    private class PositionClosedTemplate implements NotificationTemplate {
        @Override
        public String format(NotificationEvent event) {
            return String.format(
                "Positions Closed\n\n" +
                "Client: %s\n" +
                "Details: %s\n" +
                "Action: %s\n" +
                "Timestamp: %s",
                event.getClientId(),
                event.getMessage(),
                event.getAction(),
                formatDateTime(event.getTimestamp())
            );
        }

        @Override
        public String getSubject(NotificationEvent event) {
            return String.format("Positions Closed - Client: %s", event.getClientId());
        }

        @Override
        public String formatSlack(NotificationEvent event) {
            return String.format(
                ":bar_chart: *Positions Closed*\nClient: %s\n%s",
                event.getClientId(),
                event.getAction()
            );
        }
    }

    /**
     * Template for ACCOUNT_BLOCKED events
     */
    private class AccountBlockedTemplate implements NotificationTemplate {
        @Override
        public String format(NotificationEvent event) {
            boolean isPermanent = event.getMessage().contains("PERMANENT");
            return String.format(
                "Account Blocked\n\n" +
                "Client: %s\n" +
                "Type: %s\n" +
                "Reason: %s\n" +
                "Timestamp: %s",
                event.getClientId(),
                isPermanent ? "PERMANENT" : "TEMPORARY (Daily)",
                event.getMessage(),
                formatDateTime(event.getTimestamp())
            );
        }

        @Override
        public String getSubject(NotificationEvent event) {
            return String.format("[BLOCKED] Account Blocked - Client: %s", event.getClientId());
        }

        @Override
        public String formatSlack(NotificationEvent event) {
            return String.format(
                ":lock: *Account Blocked*\nClient: %s\n%s",
                event.getClientId(),
                event.getMessage()
            );
        }
    }

    /**
     * Template for MONITORING_ERROR events
     */
    private class MonitoringErrorTemplate implements NotificationTemplate {
        @Override
        public String format(NotificationEvent event) {
            return String.format(
                "Monitoring Error\n\n" +
                "Client: %s\n" +
                "Error: %s\n" +
                "Timestamp: %s\n\n" +
                "Please investigate immediately.",
                event.getClientId(),
                event.getMessage(),
                formatDateTime(event.getTimestamp())
            );
        }

        @Override
        public String getSubject(NotificationEvent event) {
            return String.format("[ERROR] Monitoring Error - Client: %s", event.getClientId());
        }

        @Override
        public String formatSlack(NotificationEvent event) {
            return String.format(
                ":fire: *Monitoring Error*\nClient: %s\n```%s```",
                event.getClientId(),
                event.getMessage()
            );
        }
    }

    /**
     * Template for SYSTEM_EVENT events
     */
    private class SystemEventTemplate implements NotificationTemplate {
        @Override
        public String format(NotificationEvent event) {
            return String.format(
                "System Event\n\n" +
                "Event: %s\n" +
                "Details: %s\n" +
                "Timestamp: %s",
                event.getEventType(),
                event.getMessage(),
                formatDateTime(event.getTimestamp())
            );
        }

        @Override
        public String getSubject(NotificationEvent event) {
            return String.format("System Event: %s", event.getEventType());
        }

        @Override
        public String formatSlack(NotificationEvent event) {
            return String.format(
                ":gear: *System Event*\n%s",
                event.getMessage()
            );
        }
    }
}