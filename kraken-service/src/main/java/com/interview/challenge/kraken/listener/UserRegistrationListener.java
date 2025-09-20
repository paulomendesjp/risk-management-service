package com.interview.challenge.kraken.listener;

import com.interview.challenge.kraken.model.KrakenAccountMonitoring.RiskLimit;
import com.interview.challenge.kraken.service.KrakenMonitoringService;
import com.interview.challenge.shared.client.UserServiceClient;
import com.interview.challenge.shared.event.UserRegistrationEvent;
import com.interview.challenge.shared.model.ClientConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Listener para eventos de registro de usuário
 * Inicia monitoramento automaticamente para usuários Kraken
 */
@Slf4j
@Component
public class UserRegistrationListener {

    @Autowired
    private KrakenMonitoringService monitoringService;

    @Autowired
    private UserServiceClient userServiceClient;

    /**
     * Escuta eventos de registro de usuário
     * Inicia monitoramento se exchange = KRAKEN
     */
    @RabbitListener(queues = "user.registrations")
    public void handleUserRegistration(UserRegistrationEvent event) {
        log.info("🎯 RECEIVED USER REGISTRATION EVENT: {}", event);

        if (event == null) {
            log.error("❌ Received null event!");
            return;
        }

        String clientId = event.getClientId();
        String eventExchange = event.getExchange();

        log.info("🆔 Processing registration for clientId: {}, exchange from event: {}", clientId, eventExchange);

        try {
            // Buscar configuração completa do usuário para verificar o exchange
            ClientConfiguration config = userServiceClient.getClientConfiguration(clientId);
            if (config == null) {
                log.warn("User {} not found", clientId);
                return;
            }

            String exchange = config.getExchange();
            if (!"KRAKEN".equalsIgnoreCase(exchange)) {
                log.debug("User {} is not a Kraken user (exchange: {}), skipping", clientId, exchange);
                return;
            }

            log.info("🦑 New Kraken user registered: {}", clientId);

            // Aguardar um pouco para garantir que os dados foram salvos
            Thread.sleep(2000);

            // Buscar credenciais descriptografadas do User Service
            log.info("🔑 Fetching credentials for Kraken user: {}", clientId);
            Map<String, String> credentials = userServiceClient.getDecryptedCredentials(clientId);

            if (credentials == null || credentials.get("apiKey") == null) {
                log.error("❌ Failed to get credentials for Kraken user: {}", clientId);
                return;
            }

            String apiKey = credentials.get("apiKey");
            String apiSecret = credentials.get("apiSecret");

            // Converter limites de risco
            RiskLimit dailyRisk = convertRiskLimit(event.getDailyRiskType(), event.getDailyRiskValue());
            RiskLimit maxRisk = convertRiskLimit(event.getMaxRiskType(), event.getMaxRiskValue());

            // Iniciar monitoramento
            log.info("🚀 Starting Kraken monitoring for user: {}", clientId);
            monitoringService.startMonitoring(
                clientId,
                apiKey,
                apiSecret,
                event.getInitialBalance(),
                dailyRisk,
                maxRisk
            );

            log.info("✅ Kraken monitoring activated for user: {}", clientId);

        } catch (Exception e) {
            log.error("❌ Failed to start Kraken monitoring for user {}: {}", clientId, e.getMessage(), e);
        }
    }

    /**
     * Escuta eventos de atualização de limites de risco
     */
    @RabbitListener(queues = "user.updates")
    public void handleUserUpdate(UserRegistrationEvent event) {
        String clientId = event.getClientId();

        try {
            // Verificar se é um usuário Kraken monitorado
            if (!monitoringService.isMonitoring(clientId)) {
                return;
            }

            log.info("🔄 Updating risk limits for Kraken user: {}", clientId);

            // Converter novos limites
            RiskLimit dailyRisk = convertRiskLimit(event.getDailyRiskType(), event.getDailyRiskValue());
            RiskLimit maxRisk = convertRiskLimit(event.getMaxRiskType(), event.getMaxRiskValue());

            // Atualizar limites no monitoramento
            monitoringService.updateRiskLimits(clientId, dailyRisk, maxRisk);

            log.info("✅ Risk limits updated for Kraken user: {}", clientId);

        } catch (Exception e) {
            log.error("❌ Failed to update risk limits for Kraken user {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Converte limite de risco do evento para formato interno
     */
    private RiskLimit convertRiskLimit(String type, BigDecimal value) {
        if (type == null || value == null) {
            return null;
        }
        return RiskLimit.builder()
            .type(type)
            .value(value)
            .build();
    }
}
