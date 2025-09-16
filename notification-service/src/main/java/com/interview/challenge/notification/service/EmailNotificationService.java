package com.interview.challenge.notification.service;

import com.interview.challenge.notification.channel.NotificationChannel;
import com.interview.challenge.notification.template.NotificationTemplateService;
import com.interview.challenge.shared.enums.NotificationPriority;
import com.interview.challenge.shared.event.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Email notification channel implementation
 * Handles email notifications for risk events
 */
@Service
public class EmailNotificationService implements NotificationChannel {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final NotificationTemplateService templateService;

    @Value("${notification.email.enabled:false}")
    private boolean emailEnabled;
    
    @Value("${notification.email.from:noreply@riskmanagement.com}")
    private String fromEmail;
    
    @Value("${notification.email.to:admin@riskmanagement.com}")
    private String toEmail;
    
    @Value("${notification.email.subject.prefix:[RISK ALERT]}")
    private String subjectPrefix;

    public EmailNotificationService(JavaMailSender mailSender, NotificationTemplateService templateService) {
        this.mailSender = mailSender;
        this.templateService = templateService;
    }

    @Override
    public boolean send(NotificationEvent event) {
        if (!isEnabled()) {
            logger.debug("Email notifications disabled, skipping: {}", event.getEventType());
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(templateService.getEmailSubject(event));
            message.setText(templateService.getFormattedMessage(event));

            mailSender.send(message);

            logger.info("Email sent successfully for event: {} (client: {})",
                       event.getEventType(), event.getClientId());
            return true;

        } catch (Exception e) {
            logger.error("Failed to send email for event {}: {}",
                        event.getEventType(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isEnabled() {
        return emailEnabled;
    }

    @Override
    public String getChannelName() {
        return "EMAIL";
    }

    @Override
    public boolean shouldHandle(NotificationEvent event) {
        if (!isEnabled()) {
            return false;
        }

        NotificationPriority priority = event.getPriority();
        return priority == NotificationPriority.CRITICAL ||
               priority == NotificationPriority.HIGH;
    }

}
