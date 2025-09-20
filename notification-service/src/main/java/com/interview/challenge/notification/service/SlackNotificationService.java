package com.interview.challenge.notification.service;

import com.interview.challenge.notification.template.NotificationTemplateService;
import com.interview.challenge.shared.event.NotificationEvent;
import com.interview.challenge.shared.enums.NotificationEventType;
import com.interview.challenge.shared.enums.NotificationPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 💬 SLACK NOTIFICATION SERVICE
 * 
 * Handles Slack notifications for critical risk events
 * Implements Requirement 5: "Optional: Slack notifications"
 */
@Service
public class SlackNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(SlackNotificationService.class);

    private final RestTemplate restTemplate;
    private final NotificationTemplateService templateService;

    @Value("${notification.slack.enabled:false}")
    private boolean slackEnabled;
    
    @Value("${notification.slack.webhook.url:}")
    private String webhookUrl;
    
    @Value("${notification.slack.channel:#risk-alerts}")
    private String channel;
    
    @Value("${notification.slack.username:Risk Management Bot}")
    private String username;

    public SlackNotificationService(RestTemplate restTemplate, NotificationTemplateService templateService) {
        this.restTemplate = restTemplate;
        this.templateService = templateService;
    }

    /**
     * Send Slack notification for critical risk events
     */
    public void sendNotification(NotificationEvent event) {
        if (!slackEnabled || webhookUrl.isEmpty()) {
            logger.debug("💬 Slack notifications disabled or webhook URL not configured, skipping for: {}", 
                        event.getEventType());
            return;
        }

        try {
            Map<String, Object> slackMessage = buildSlackMessage(event);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(slackMessage, headers);
            
            restTemplate.postForEntity(webhookUrl, request, String.class);
            
            logger.info("💬 Slack notification sent successfully for event: {} (client: {})", 
                       event.getEventType(), event.getClientId());
                       
        } catch (Exception e) {
            logger.error("❌ Failed to send Slack notification for event {}: {}", 
                        event.getEventType(), e.getMessage());
            throw new RuntimeException("Slack notification failed", e);
        }
    }

    /**
     * Build Slack message payload
     */
    private Map<String, Object> buildSlackMessage(NotificationEvent event) {
        Map<String, Object> message = new HashMap<>();

        message.put("channel", channel);
        message.put("username", username);
        message.put("text", templateService.getSlackMessage(event));

        // Add color based on priority
        String color = getColorForPriority(event.getPriority());
        if (color != null) {
            Map<String, Object> attachment = new HashMap<>();
            attachment.put("color", color);
            attachment.put("text", event.getMessage());
            message.put("attachments", new Object[]{attachment});
        }
        
        return message;
    }

    /**
     * Get color based on notification priority
     */
    private String getColorForPriority(NotificationPriority priority) {
        switch (priority) {
            case CRITICAL:
                return "danger";  // Red
            case HIGH:
                return "warning"; // Orange
            case NORMAL:
                return "good";    // Green
            case LOW:
                return "#e3e4e6"; // Gray
            default:
                return null;
        }
    }
}



