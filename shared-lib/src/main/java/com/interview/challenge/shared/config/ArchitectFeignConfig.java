package com.interview.challenge.shared.config;

import com.interview.challenge.shared.client.ArchitectAuthInterceptor;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Architect API Feign client
 */
@Configuration
public class ArchitectFeignConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        // Use the ArchitectAuthInterceptor that handles api-key and api-secret headers
        return new ArchitectAuthInterceptor();
    }
}