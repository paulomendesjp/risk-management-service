package com.interview.challenge.kraken.listener;

import com.interview.challenge.kraken.config.RabbitMQConfig;
import com.interview.challenge.kraken.dto.KrakenOrderRequest;
import com.interview.challenge.kraken.service.KrakenTradingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listener para processar ordens do Kraken via fila RabbitMQ
 *
 * Fluxo:
 * 1. API Gateway recebe webhook
 * 2. Gateway publica mensagem na fila
 * 3. Este listener processa a mensagem
 * 4. Executa ordem no Kraken
 */
@Slf4j
@Component
public class KrakenOrderListener {

    @Autowired
    private KrakenTradingService tradingService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * Processa ordens recebidas da fila
     *
     * @param orderRequest Requisição de ordem do webhook
     */
    @RabbitListener(queues = RabbitMQConfig.KRAKEN_ORDERS_QUEUE)
    public void processKrakenOrder(Map<String, Object> webhookData) {
        try {
            log.info("📨 Mensagem recebida da fila: clientId={}, action={}, symbol={}",
                    webhookData.get("clientId"),
                    webhookData.get("action"),
                    webhookData.get("symbol"));

            // Extrair credenciais (devem vir junto ou buscar do banco)
            String apiKey = (String) webhookData.get("apiKey");
            String apiSecret = (String) webhookData.get("apiSecret");

            if (apiKey == null || apiSecret == null) {
                // Se não vieram na mensagem, buscar do banco
                String clientId = (String) webhookData.get("clientId");
                log.warn("Credenciais não encontradas na mensagem, buscar do banco para client: {}", clientId);
                // TODO: Implementar busca de credenciais do banco
                throw new IllegalArgumentException("Credenciais não fornecidas");
            }

            // Converter map para KrakenOrderRequest
            KrakenOrderRequest orderRequest = convertToOrderRequest(webhookData);

            // Processar ordem
            Map<String, Object> result = tradingService.processWebhookOrder(
                orderRequest,
                apiKey,
                apiSecret
            );

            if (Boolean.TRUE.equals(result.get("success"))) {
                log.info("✅ Ordem processada com sucesso via fila: orderId={}",
                        result.get("orderId"));

                // Publicar evento de sucesso (opcional)
                publishSuccessEvent(orderRequest, result);
            } else {
                log.error("❌ Falha ao processar ordem via fila: {}", result.get("error"));

                // Publicar evento de erro (opcional)
                publishErrorEvent(orderRequest, result);
            }

        } catch (Exception e) {
            log.error("❌ Erro ao processar mensagem da fila: {}", e.getMessage(), e);

            // A mensagem será enviada para DLQ automaticamente após várias tentativas
            throw new RuntimeException("Erro no processamento: " + e.getMessage(), e);
        }
    }

    /**
     * Listener para Dead Letter Queue - mensagens com erro
     */
    @RabbitListener(queues = RabbitMQConfig.KRAKEN_ORDERS_DLQ)
    public void handleFailedOrder(Map<String, Object> webhookData) {
        log.error("⚠️ Mensagem na DLQ - investigar manualmente: {}", webhookData);

        // Aqui você pode:
        // 1. Salvar em banco para análise posterior
        // 2. Enviar notificação para equipe
        // 3. Tentar reprocessar com lógica diferente

        // Por enquanto, apenas loga
        String clientId = (String) webhookData.get("clientId");
        log.error("Order failed for client {}: {}", clientId, webhookData);
    }

    /**
     * Converte Map para KrakenOrderRequest
     */
    private KrakenOrderRequest convertToOrderRequest(Map<String, Object> data) {
        return KrakenOrderRequest.builder()
                .clientId((String) data.get("clientId"))
                .symbol((String) data.get("symbol"))
                .side((String) data.get("action"))
                .orderQty(convertToBigDecimal(data.get("orderQty")))
                .orderType((String) data.getOrDefault("orderType", "mkt"))
                .strategy((String) data.get("strategy"))
                .maxRiskPerDay(convertToBigDecimal(data.get("maxriskperday%")))
                .stopLossPercentage(convertToBigDecimal(data.get("stopLoss%")))
                .inverse((Boolean) data.getOrDefault("inverse", false))
                .pyramid((Boolean) data.getOrDefault("pyramid", false))
                .source("rabbitmq")
                .build();
    }

    /**
     * Converte objeto para BigDecimal
     */
    private java.math.BigDecimal convertToBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof java.math.BigDecimal) return (java.math.BigDecimal) value;
        if (value instanceof Number) return java.math.BigDecimal.valueOf(((Number) value).doubleValue());
        if (value instanceof String) return new java.math.BigDecimal((String) value);
        return null;
    }

    /**
     * Publica evento de sucesso
     */
    private void publishSuccessEvent(KrakenOrderRequest order, Map<String, Object> result) {
        Map<String, Object> event = Map.of(
            "type", "ORDER_SUCCESS",
            "clientId", order.getClientId(),
            "orderId", result.get("orderId"),
            "symbol", order.getSymbol(),
            "timestamp", System.currentTimeMillis()
        );

        rabbitTemplate.convertAndSend("events.exchange", "order.success", event);
    }

    /**
     * Publica evento de erro
     */
    private void publishErrorEvent(KrakenOrderRequest order, Map<String, Object> result) {
        Map<String, Object> event = Map.of(
            "type", "ORDER_ERROR",
            "clientId", order.getClientId(),
            "error", result.get("error"),
            "symbol", order.getSymbol(),
            "timestamp", System.currentTimeMillis()
        );

        rabbitTemplate.convertAndSend("events.exchange", "order.error", event);
    }
}