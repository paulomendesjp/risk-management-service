package com.interview.challenge.risk.controller;

import com.interview.challenge.risk.service.RiskMonitoringService;
import com.interview.challenge.shared.event.BalanceUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 🧪 CONTROLLER DE TESTE PARA SIMULAR CENÁRIOS DE RISCO
 *
 * ATENÇÃO: Use apenas para testes! Não deve estar habilitado em produção.
 * Permite simular perdas e testar o sistema de risk management.
 */
@RestController
@RequestMapping("/api/risk/test")
@CrossOrigin(origins = "*")
public class RiskTestController {

    private static final Logger logger = LoggerFactory.getLogger(RiskTestController.class);

    @Autowired
    private RiskMonitoringService riskMonitoringService;

    /**
     * Simula uma atualização de saldo para testar violações de risco
     *
     * Exemplo de uso:
     * POST http://localhost:8083/api/risk/test/simulate-balance
     * {
     *   "clientId": "jpmendesnas19",
     *   "newBalance": -2.0,
     *   "previousBalance": 0.0
     * }
     */
    @PostMapping("/simulate-balance")
    public ResponseEntity<Map<String, Object>> simulateBalanceUpdate(@RequestBody SimulateBalanceRequest request) {
        logger.warn("⚠️ SIMULAÇÃO DE TESTE: Atualizando saldo para cliente {} de {} para {}",
                    request.getClientId(), request.getPreviousBalance(), request.getNewBalance());

        try {
            // Criar mensagem de atualização de saldo
            BalanceUpdateEvent balanceUpdate = new BalanceUpdateEvent();
            balanceUpdate.setClientId(request.getClientId());
            balanceUpdate.setNewBalance(request.getNewBalance());
            balanceUpdate.setPreviousBalance(request.getPreviousBalance());
            balanceUpdate.setTimestamp(LocalDateTime.now());
            balanceUpdate.setSource("TEST_SIMULATION");

            // Processar atualização através do serviço de monitoramento
            riskMonitoringService.processBalanceUpdate(balanceUpdate);

            // Calcular a perda
            BigDecimal loss = request.getPreviousBalance().subtract(request.getNewBalance());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Saldo simulado com sucesso");
            response.put("clientId", request.getClientId());
            response.put("previousBalance", request.getPreviousBalance());
            response.put("newBalance", request.getNewBalance());
            response.put("simulatedLoss", loss);
            response.put("timestamp", LocalDateTime.now());

            logger.info("✅ Simulação processada: Cliente {} | Perda: ${}",
                       request.getClientId(), loss);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("❌ Erro ao simular saldo: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Simula violação de Daily Risk
     * Força uma perda que excede o limite diário
     */
    @PostMapping("/simulate-daily-risk/{clientId}")
    public ResponseEntity<Map<String, Object>> simulateDailyRiskViolation(@PathVariable String clientId) {
        logger.warn("🚨 SIMULAÇÃO DE TESTE: Forçando violação de DAILY RISK para cliente {}", clientId);

        try {
            // Simular perda de $1.50 (excede o limite de $1)
            BigDecimal currentBalance = BigDecimal.ZERO;
            BigDecimal newBalance = new BigDecimal("-1.50");

            BalanceUpdateEvent balanceUpdate = new BalanceUpdateEvent();
            balanceUpdate.setClientId(clientId);
            balanceUpdate.setNewBalance(newBalance);
            balanceUpdate.setPreviousBalance(currentBalance);
            balanceUpdate.setTimestamp(LocalDateTime.now());
            balanceUpdate.setSource("TEST_DAILY_RISK_VIOLATION");

            riskMonitoringService.processBalanceUpdate(balanceUpdate);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Daily Risk violation simulada");
            response.put("clientId", clientId);
            response.put("simulatedLoss", new BigDecimal("1.50"));
            response.put("dailyRiskLimit", new BigDecimal("1.00"));
            response.put("expectedAction", "Cliente deve ser bloqueado para o dia");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("❌ Erro ao simular daily risk: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Simula violação de Max Risk
     * Força uma perda que excede o limite máximo
     */
    @PostMapping("/simulate-max-risk/{clientId}")
    public ResponseEntity<Map<String, Object>> simulateMaxRiskViolation(@PathVariable String clientId) {
        logger.warn("🚨 SIMULAÇÃO DE TESTE: Forçando violação de MAX RISK para cliente {}", clientId);

        try {
            // Simular perda de $2.00 (excede o limite máximo de $1)
            BigDecimal currentBalance = BigDecimal.ZERO;
            BigDecimal newBalance = new BigDecimal("-2.00");

            BalanceUpdateEvent balanceUpdate = new BalanceUpdateEvent();
            balanceUpdate.setClientId(clientId);
            balanceUpdate.setNewBalance(newBalance);
            balanceUpdate.setPreviousBalance(currentBalance);
            balanceUpdate.setTimestamp(LocalDateTime.now());
            balanceUpdate.setSource("TEST_MAX_RISK_VIOLATION");

            riskMonitoringService.processBalanceUpdate(balanceUpdate);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Max Risk violation simulada");
            response.put("clientId", clientId);
            response.put("simulatedLoss", new BigDecimal("2.00"));
            response.put("maxRiskLimit", new BigDecimal("1.00"));
            response.put("expectedAction", "Cliente deve ser bloqueado permanentemente e posições fechadas");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("❌ Erro ao simular max risk: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Verifica o status de risco atual do cliente
     */
    @GetMapping("/status/{clientId}")
    public ResponseEntity<Map<String, Object>> getRiskStatus(@PathVariable String clientId) {
        try {
            var monitoringOpt = riskMonitoringService.getAccountMonitoring(clientId);

            if (!monitoringOpt.isPresent()) {
                Map<String, Object> response = new HashMap<>();
                response.put("error", "Cliente não encontrado no monitoramento");
                response.put("clientId", clientId);
                return ResponseEntity.notFound().build();
            }

            var monitoring = monitoringOpt.get();
            Map<String, Object> response = new HashMap<>();
            response.put("clientId", clientId);
            response.put("currentBalance", monitoring.getCurrentBalance());
            response.put("dailyPnl", monitoring.getDailyPnl());
            response.put("totalPnl", monitoring.getTotalPnl());
            response.put("dailyLoss", monitoring.getDailyLoss());
            response.put("totalLoss", monitoring.getCurrentLoss());
            response.put("dailyBlocked", monitoring.isDailyBlocked());
            response.put("permanentlyBlocked", monitoring.isPermanentlyBlocked());
            response.put("canTrade", monitoring.canTrade());
            response.put("riskStatus", monitoring.getRiskStatus());
            response.put("lastError", monitoring.getLastError());
            response.put("lastRiskCheck", monitoring.getLastRiskCheck());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("❌ Erro ao buscar status: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Request DTO para simulação de saldo
     */
    public static class SimulateBalanceRequest {
        private String clientId;
        private BigDecimal newBalance;
        private BigDecimal previousBalance;

        // Getters e Setters
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public BigDecimal getNewBalance() { return newBalance; }
        public void setNewBalance(BigDecimal newBalance) { this.newBalance = newBalance; }

        public BigDecimal getPreviousBalance() { return previousBalance; }
        public void setPreviousBalance(BigDecimal previousBalance) { this.previousBalance = previousBalance; }
    }
}