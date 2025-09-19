package com.interview.challenge.notification.controller;

import com.interview.challenge.notification.service.NotificationOrchestrator;
import com.interview.challenge.shared.event.NotificationEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 📢 NOTIFICATION CONTROLLER
 * 
 * REST API for notification service monitoring and management
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationOrchestrator notificationOrchestrator;

    /**
     * Get notification service health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "notification-service",
            "timestamp", java.time.LocalDateTime.now()
        ));
    }

    /**
     * Get notification statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<NotificationOrchestrator.NotificationStatistics> getStatistics() {
        NotificationOrchestrator.NotificationStatistics stats = notificationOrchestrator.getStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Send test notification (for testing purposes)
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, String>> sendTestNotification(@RequestBody Map<String, String> request) {
        try {
            String clientId = request.getOrDefault("clientId", "test-client");
            String eventType = request.getOrDefault("eventType", "SYSTEM_EVENT");
            String message = request.getOrDefault("message", "Test notification");
            
            NotificationEvent testEvent = NotificationEvent.systemEvent(eventType, message);
            testEvent.setClientId(clientId);
            
            // Process test notification
            notificationOrchestrator.handleNotificationEvent(testEvent);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Test notification sent successfully"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to send test notification: " + e.getMessage()
            ));
        }
    }
}


