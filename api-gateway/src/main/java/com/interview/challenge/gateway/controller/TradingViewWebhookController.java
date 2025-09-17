package com.interview.challenge.gateway.controller;

import com.interview.challenge.gateway.dto.TradingViewWebhookRequest;
import com.interview.challenge.gateway.service.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Controller to handle TradingView webhook requests
 * TradingView only supports webhooks on port 80/443
 */
@RestController
@RequestMapping("/webhook")
@CrossOrigin(origins = "*")
public class TradingViewWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(TradingViewWebhookController.class);

    @Autowired
    private WebhookService webhookService;

    /**
     * Receive webhook from TradingView and route to appropriate service
     */
    @PostMapping("/tradingview")
    public Mono<ResponseEntity<Map<String, Object>>> handleTradingViewWebhook(
            @RequestBody TradingViewWebhookRequest request,
            @RequestHeader(value = "X-TradingView-Signature", required = false) String signature) {

        logger.info("📊 Received TradingView webhook for client: {} - Action: {} - Symbol: {}",
                request.getClientId(), request.getAction(), request.getSymbol());

        // Log the complete request for audit
        logger.debug("Full webhook payload: {}", request);

        return webhookService.processTradingViewWebhook(request)
                .map(result -> {
                    logger.info("✅ Webhook processed successfully for client: {}", request.getClientId());
                    return ResponseEntity.ok(result);
                })
                .onErrorResume(error -> {
                    logger.error("❌ Error processing webhook for client {}: {}",
                            request.getClientId(), error.getMessage());

                    Map<String, Object> errorResponse = Map.of(
                            "success", false,
                            "error", error.getMessage(),
                            "clientId", request.getClientId()
                    );

                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(errorResponse));
                });
    }

    /**
     * Health check endpoint for TradingView
     */
    @GetMapping("/tradingview/health")
    public Mono<ResponseEntity<Map<String, String>>> healthCheck() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "tradingview-webhook",
                "timestamp", String.valueOf(System.currentTimeMillis())
        )));
    }

    /**
     * Test endpoint to validate webhook configuration
     */
    @PostMapping("/tradingview/test")
    public Mono<ResponseEntity<Map<String, Object>>> testWebhook(@RequestBody Map<String, Object> payload) {
        logger.info("📝 Test webhook received: {}", payload);

        return Mono.just(ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Test webhook received successfully",
                "echo", payload,
                "timestamp", System.currentTimeMillis()
        )));
    }
}