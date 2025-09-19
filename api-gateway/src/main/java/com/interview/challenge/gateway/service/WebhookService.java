package com.interview.challenge.gateway.service;

import com.interview.challenge.gateway.dto.TradingViewWebhookRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to process TradingView webhooks and route to appropriate services
 */
@Service
public class WebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);

    private final WebClient webClient;

    @Value("${position.service.url:http://position-service:8082}")
    private String positionServiceUrl;

    @Value("${user.service.url:http://user-service:8081}")
    private String userServiceUrl;

    @Value("${architect.bridge.url:http://architect-bridge:8090}")
    private String architectBridgeUrl;

    @Value("${kraken.service.url:http://kraken-service:8086}")
    private String krakenServiceUrl;

    @Value("${webhook.processing.use-queue:false}")
    private boolean useQueueProcessing;

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @Autowired
    public WebhookService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * Process TradingView webhook and route to appropriate service
     */
    public Mono<Map<String, Object>> processTradingViewWebhook(TradingViewWebhookRequest request) {
        // Validate request
        if (!request.isValid()) {
            logger.error("Invalid webhook request: {}", request);
            return Mono.error(new IllegalArgumentException("Invalid webhook request"));
        }

        logger.info("Processing TradingView webhook for client: {} - Action: {} - Exchange: {}",
                request.getClientId(), request.getNormalizedAction(), request.getTargetExchange());

        // Route to appropriate exchange service
        if (request.isKrakenOrder()) {
            return processKrakenWebhook(request);
        } else {
            return processArchitectWebhook(request);
        }
    }

    /**
     * Process webhook for Kraken exchange
     */
    private Mono<Map<String, Object>> processKrakenWebhook(TradingViewWebhookRequest request) {
        logger.info("🦑 Routing to Kraken service for client: {}", request.getClientId());

        // Decide se usa fila ou chamada direta
        if (useQueueProcessing && rabbitTemplate != null) {
            return processKrakenViaQueue(request);
        } else {
            return processKrakenDirectly(request);
        }
    }

    /**
     * Processa Kraken via fila RabbitMQ (assíncrono)
     */
    private Mono<Map<String, Object>> processKrakenViaQueue(TradingViewWebhookRequest request) {
        logger.info("📨 Enviando ordem para fila RabbitMQ: {}", request.getClientId());

        return getClientCredentials(request.getClientId())
                .flatMap(credentials -> {
                    try {
                        // Preparar mensagem com credenciais
                        Map<String, Object> message = new HashMap<>();
                        message.put("clientId", request.getClientId());
                        message.put("symbol", request.getSymbol());
                        message.put("action", request.getAction());
                        message.put("orderQty", request.getOrderQty());
                        message.put("strategy", request.getStrategy());
                        message.put("exchange", request.getExchange());
                        message.put("maxriskperday%", request.getMaxRiskPerDay());
                        message.put("stopLoss%", request.getStopLossPercentage());
                        message.put("inverse", request.getInverse());
                        message.put("pyramid", request.getPyramid());
                        message.put("apiKey", credentials.get("apiKey"));
                        message.put("apiSecret", credentials.get("apiSecret"));

                        // Enviar para fila
                        rabbitTemplate.convertAndSend(
                            "trading.exchange",
                            "trading.kraken",
                            message
                        );

                        logger.info("✅ Mensagem enviada para fila com sucesso");

                        // Retornar resposta de aceito
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("status", "QUEUED");
                        response.put("message", "Order queued for processing");
                        response.put("clientId", request.getClientId());
                        response.put("timestamp", System.currentTimeMillis());

                        return Mono.just(response);

                    } catch (Exception e) {
                        logger.error("❌ Erro ao enviar para fila: {}", e.getMessage());
                        return Mono.error(new RuntimeException("Failed to queue order: " + e.getMessage()));
                    }
                });
    }

    /**
     * Processa Kraken diretamente via HTTP (síncrono)
     */
    private Mono<Map<String, Object>> processKrakenDirectly(TradingViewWebhookRequest request) {
        logger.info("🔄 Processando ordem diretamente via HTTP");

        return getClientCredentials(request.getClientId())
                .flatMap(credentials -> {
                    String apiKey = (String) credentials.get("apiKey");
                    String apiSecret = (String) credentials.get("apiSecret");

                    if (apiKey == null || apiSecret == null) {
                        logger.error("No credentials found for client: {}", request.getClientId());
                        return Mono.error(new IllegalStateException("Client credentials not found"));
                    }

                    String url = krakenServiceUrl + "/api/kraken/webhook";

                    return webClient.post()
                            .uri(url)
                            .header("X-API-KEY", apiKey)
                            .header("X-API-SECRET", apiSecret)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .map(response -> (Map<String, Object>) response)
                            .timeout(Duration.ofSeconds(30))
                            .doOnSuccess(response -> logger.info("✅ Kraken webhook processed successfully"))
                            .onErrorResume(error -> {
                                logger.error("❌ Kraken webhook processing failed: {}", error.getMessage());
                                return Mono.error(new RuntimeException("Kraken processing failed: " + error.getMessage()));
                            });
                });
    }

    /**
     * Process webhook for Architect exchange (existing logic)
     */
    private Mono<Map<String, Object>> processArchitectWebhook(TradingViewWebhookRequest request) {
        logger.info("🏛️ Routing to Architect service for client: {}", request.getClientId());

        // First, get client credentials from user service
        return getClientCredentials(request.getClientId())
                .flatMap(credentials -> {
                    String apiKey = (String) credentials.get("apiKey");
                    String apiSecret = (String) credentials.get("apiSecret");

                    if (apiKey == null || apiSecret == null) {
                        logger.error("No credentials found for client: {}", request.getClientId());
                        return Mono.error(new IllegalStateException("Client credentials not found"));
                    }

                    // Route based on action
                    if (isOrderAction(request.getNormalizedAction())) {
                        return createOrder(request, apiKey, apiSecret);
                    } else if (isCloseAction(request.getNormalizedAction())) {
                        return closePositions(request, apiKey, apiSecret);
                    } else {
                        return Mono.error(new IllegalArgumentException("Unknown action: " + request.getAction()));
                    }
                });
    }

    /**
     * Get client credentials from user service
     */
    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> getClientCredentials(String clientId) {
        String url = userServiceUrl + "/api/users/" + clientId + "/credentials";

        logger.debug("Fetching credentials for client: {}", clientId);

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .map(map -> (Map<String, Object>) map)
                .timeout(Duration.ofSeconds(5))
                .doOnSuccess(creds -> logger.debug("Retrieved credentials for client: {}", clientId))
                .onErrorResume(error -> {
                    logger.error("Failed to get credentials for client {}: {}", clientId, error.getMessage());
                    return Mono.error(new RuntimeException("Failed to get client credentials"));
                });
    }

    /**
     * Create order through position service
     */
    private Mono<Map<String, Object>> createOrder(TradingViewWebhookRequest request,
                                                   String apiKey, String apiSecret) {
        String url = positionServiceUrl + "/api/positions/orders";

        Map<String, Object> orderRequest = new HashMap<>();
        orderRequest.put("clientId", request.getClientId());
        orderRequest.put("symbol", request.getSymbol());
        orderRequest.put("action", request.getNormalizedAction());
        orderRequest.put("orderQty", request.getOrderQty());
        orderRequest.put("orderType", request.getOrderTypeOrDefault());

        if (request.getLimitPrice() != null) {
            orderRequest.put("limitPrice", request.getLimitPrice());
        }
        if (request.getStopLoss() != null) {
            orderRequest.put("stopLoss", request.getStopLoss());
        }
        if (request.getTakeProfit() != null) {
            orderRequest.put("takeProfit", request.getTakeProfit());
        }
        if (request.getStrategy() != null) {
            orderRequest.put("strategy", request.getStrategy());
        }

        logger.info("📈 Creating order for client {} - Symbol: {} - Action: {} - Qty: {}",
                request.getClientId(), request.getSymbol(),
                request.getNormalizedAction(), request.getOrderQty());

        return webClient.post()
                .uri(url)
                .header("X-API-KEY", apiKey)
                .header("X-API-SECRET", apiSecret)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(orderRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .map(response -> {
                    logger.info("✅ Order created successfully for client: {}", request.getClientId());
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("action", "order_created");
                    result.put("order", response);
                    result.put("clientId", request.getClientId());
                    return result;
                })
                .onErrorResume(error -> {
                    logger.error("❌ Failed to create order for client {}: {}",
                            request.getClientId(), error.getMessage());
                    return Mono.error(new RuntimeException("Failed to create order: " + error.getMessage()));
                });
    }

    /**
     * Close positions through position service
     */
    private Mono<Map<String, Object>> closePositions(TradingViewWebhookRequest request,
                                                      String apiKey, String apiSecret) {
        String url = positionServiceUrl + "/api/positions/" + request.getClientId() + "/close-all";

        logger.info("📉 Closing positions for client: {}", request.getClientId());

        return webClient.post()
                .uri(url + "?reason=TRADINGVIEW_SIGNAL")
                .header("X-API-KEY", apiKey)
                .header("X-API-SECRET", apiSecret)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    logger.info("✅ Positions closed successfully for client: {}", request.getClientId());
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("action", "positions_closed");
                    result.put("result", response);
                    result.put("clientId", request.getClientId());
                    return result;
                })
                .onErrorResume(error -> {
                    logger.error("❌ Failed to close positions for client {}: {}",
                            request.getClientId(), error.getMessage());
                    return Mono.error(new RuntimeException("Failed to close positions: " + error.getMessage()));
                });
    }

    /**
     * Check if action is an order action
     */
    private boolean isOrderAction(String action) {
        return "BUY".equals(action) || "SELL".equals(action)
                || "LONG".equals(action) || "SHORT".equals(action);
    }

    /**
     * Check if action is a close action
     */
    private boolean isCloseAction(String action) {
        return "CLOSE".equals(action) || "CLOSE_ALL".equals(action)
                || "EXIT".equals(action) || "FLATTEN".equals(action);
    }
}