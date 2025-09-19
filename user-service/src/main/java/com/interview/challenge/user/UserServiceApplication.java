package com.interview.challenge.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * User Service Application
 * 
 * Handles user registration, configuration, and API credential management
 * for the Risk Management System
 */
@SpringBootApplication(scanBasePackages = {
    "com.interview.challenge.user",
    "com.interview.challenge.shared"
})
@EnableFeignClients(basePackages = "com.interview.challenge.shared")
@EnableMongoRepositories(basePackages = "com.interview.challenge.user.repository")
@EnableAsync
@EnableScheduling
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}



