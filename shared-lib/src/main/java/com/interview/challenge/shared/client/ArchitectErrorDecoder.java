package com.interview.challenge.shared.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Custom error decoder for Architect API responses
 * Converts HTTP errors into meaningful exceptions
 */
public class ArchitectErrorDecoder implements ErrorDecoder {

    private static final Logger logger = LoggerFactory.getLogger(ArchitectErrorDecoder.class);
    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        String errorBody = getErrorBody(response);
        
        logger.error("🚨 Architect API error [{}]: {} - {}", 
                response.status(), methodKey, errorBody);

        switch (response.status()) {
            case 400:
                return new ArchitectApiException("Bad Request: " + errorBody, response.status());
            case 401:
                return new ArchitectApiException("Invalid API credentials", response.status());
            case 403:
                return new ArchitectApiException("Insufficient permissions", response.status());
            case 404:
                return new ArchitectApiException("Resource not found: " + errorBody, response.status());
            case 429:
                return new ArchitectApiException("Rate limit exceeded. Please try again later.", response.status());
            case 500:
                return new ArchitectApiException("Architect API internal error", response.status());
            case 502:
            case 503:
            case 504:
                return new ArchitectApiException("Architect API temporarily unavailable", response.status());
            default:
                return defaultErrorDecoder.decode(methodKey, response);
        }
    }

    /**
     * Extract error body from response
     */
    private String getErrorBody(Response response) {
        try {
            if (response.body() != null) {
                return new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.warn("Failed to read error response body: {}", e.getMessage());
        }
        return "Unknown error";
    }

    /**
     * Custom exception for Architect API errors
     */
    public static class ArchitectApiException extends RuntimeException {
        private final int statusCode;

        public ArchitectApiException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public boolean isRetryable() {
            // Retry on 5xx errors and rate limits
            return statusCode >= 500 || statusCode == 429;
        }
    }
}

