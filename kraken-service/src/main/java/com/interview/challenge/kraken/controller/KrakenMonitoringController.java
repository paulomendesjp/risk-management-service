package com.interview.challenge.kraken.controller;

import com.interview.challenge.kraken.model.KrakenAccountMonitoring.RiskLimit;
import com.interview.challenge.kraken.service.KrakenMonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/kraken/monitoring")
public class KrakenMonitoringController {

    @Autowired
    private KrakenMonitoringService monitoringService;

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startMonitoring(@RequestBody StartMonitoringRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Starting Kraken monitoring for client: {}", request.getClientId());

            monitoringService.startMonitoring(
                request.getClientId(),
                request.getApiKey(),
                request.getApiSecret(),
                request.getInitialBalance(),
                request.getDailyRisk(),
                request.getMaxRisk()
            );

            response.put("success", true);
            response.put("message", "Monitoring started successfully");
            response.put("clientId", request.getClientId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error starting monitoring for client {}: {}", request.getClientId(), e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/stop/{clientId}")
    public ResponseEntity<Map<String, Object>> stopMonitoring(@PathVariable String clientId) {
        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Stopping Kraken monitoring for client: {}", clientId);

            monitoringService.stopMonitoring(clientId);

            response.put("success", true);
            response.put("message", "Monitoring stopped successfully");
            response.put("clientId", clientId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error stopping monitoring for client {}: {}", clientId, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/status/{clientId}")
    public ResponseEntity<Map<String, Object>> getMonitoringStatus(@PathVariable String clientId) {
        try {
            log.debug("Getting monitoring status for client: {}", clientId);

            Map<String, Object> status = monitoringService.getMonitoringStatus(clientId);
            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Error getting monitoring status for client {}: {}", clientId, e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    public static class StartMonitoringRequest {
        private String clientId;
        private String apiKey;
        private String apiSecret;
        private BigDecimal initialBalance;
        private RiskLimit dailyRisk;
        private RiskLimit maxRisk;

        // Getters and setters
        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiSecret() {
            return apiSecret;
        }

        public void setApiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
        }

        public BigDecimal getInitialBalance() {
            return initialBalance;
        }

        public void setInitialBalance(BigDecimal initialBalance) {
            this.initialBalance = initialBalance;
        }

        public RiskLimit getDailyRisk() {
            return dailyRisk;
        }

        public void setDailyRisk(RiskLimit dailyRisk) {
            this.dailyRisk = dailyRisk;
        }

        public RiskLimit getMaxRisk() {
            return maxRisk;
        }

        public void setMaxRisk(RiskLimit maxRisk) {
            this.maxRisk = maxRisk;
        }
    }
}