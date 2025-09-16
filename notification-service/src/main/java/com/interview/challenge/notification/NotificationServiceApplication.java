package com.interview.challenge.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Notification Service Application
 *
 * Centralizes all system notifications and alerts
 * Supports multiple delivery channels: logs, email, Slack, WebSocket
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.interview.challenge.shared", "com.interview.challenge.notification"})
@EnableFeignClients(basePackages = "com.interview.challenge.shared.client")
@EnableAsync
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
