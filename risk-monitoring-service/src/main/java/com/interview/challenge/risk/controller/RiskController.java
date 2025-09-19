package com.interview.challenge.risk.controller;

import com.interview.challenge.risk.constants.RiskConstants;
import com.interview.challenge.risk.dto.ApiResponse;
import com.interview.challenge.risk.service.RiskMonitoringService;
import com.interview.challenge.shared.event.BalanceUpdateEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/risk")
@CrossOrigin(origins = "*")
public class RiskController {

    private static final Logger logger = LoggerFactory.getLogger(RiskController.class);

    @Autowired
    private RiskMonitoringService riskMonitoringService;

    @GetMapping("/status/{clientId}")
    public ResponseEntity<ApiResponse> getRiskStatus(@PathVariable String clientId) {
        try {
            Map<String, Object> status = riskMonitoringService.getRiskStatus(clientId);
            return ResponseEntity.ok(
                ApiResponse.success()
                    .data(status)
                    .build()
            );
        } catch (Exception e) {
            logger.error("Error getting risk status for client {}: {}", clientId, e.getMessage());
            return ResponseEntity.internalServerError().body(
                ApiResponse.error()
                    .message("Error getting risk status: " + e.getMessage())
                    .build()
            );
        }
    }

    @PostMapping("/update-balance/{clientId}")
    public ResponseEntity<ApiResponse> updateBalance(@PathVariable String clientId) {
        try {
            riskMonitoringService.forceBalanceUpdate(clientId);
            return ResponseEntity.ok(
                ApiResponse.success()
                    .data("status", RiskConstants.STATUS_UPDATED)
                    .data("clientId", clientId)
                    .build()
            );
        } catch (Exception e) {
            logger.error("Error updating balance for client {}: {}", clientId, e.getMessage());
            return ResponseEntity.internalServerError().body(
                ApiResponse.error()
                    .message("Error updating balance: " + e.getMessage())
                    .build()
            );
        }
    }

    /**
     * 🌐 WEBSOCKET ENDPOINT - Receives real-time balance updates from Python Bridge
     * 
     * This endpoint implements the requirement:
     * "Continuously fetch account balances in real-time (WebSocket or polling)"
     */
    @PostMapping("/balance-update")
    public ResponseEntity<ApiResponse> receiveBalanceUpdate(@RequestBody Map<String, Object> balanceData) {
        try {
            logger.info("📡 Received real-time balance update: {}", balanceData);
            
            String clientId = extractClientId(balanceData);
            BigDecimal newBalance = extractBalance(balanceData);

            if (clientId == null || newBalance == null) {
                return ResponseEntity.badRequest().body(
                    ApiResponse.error()
                        .message("Missing required fields: client_id, balance")
                        .build()
                );
            }

            BalanceUpdateEvent event = createBalanceUpdateEvent(balanceData, clientId, newBalance);
            riskMonitoringService.processBalanceUpdate(event);

            logger.info("✅ Processed balance update for client {}: ${}", clientId, newBalance);

            return ResponseEntity.ok(
                ApiResponse.success()
                    .data("status", RiskConstants.STATUS_PROCESSED)
                    .data("clientId", clientId)
                    .data("balance", newBalance)
                    .build()
            );
            
        } catch (Exception e) {
            logger.error("❌ Error processing balance update: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                ApiResponse.error()
                    .message("Error processing balance update: " + e.getMessage())
                    .build()
            );
        }
    }

    /**
     * 🚨 MONITORING ERROR ENDPOINT - Receives monitoring errors from Python Bridge
     */
    @PostMapping("/monitoring-error")
    public ResponseEntity<?> receiveMonitoringError(@RequestBody Map<String, Object> errorData) {
        try {
            String clientId = (String) errorData.get("client_id");
            String error = (String) errorData.get("error");

            logger.error("🚨 Monitoring error for client {}: {}", clientId, error);
            riskMonitoringService.handleMonitoringError(clientId, error);

            return ResponseEntity.ok(
                ApiResponse.success()
                    .data("status", RiskConstants.STATUS_ERROR_LOGGED)
                    .data("clientId", clientId)
                    .build()
            );
            
        } catch (Exception e) {
            logger.error("❌ Error handling monitoring error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(
                ApiResponse.error()
                    .message("Error handling monitoring error: " + e.getMessage())
                    .build()
            );
        }
    }

    /**
     * 🚀 START REAL-TIME MONITORING - Triggers Python Bridge to start WebSocket monitoring
     */
    // WebSocket monitoring endpoint removed - monitoring is now handled automatically by BalanceWebSocketClient
    // The BalanceWebSocketClient connects to all monitored clients on startup

    // Stop monitoring endpoint also removed - WebSocket connections are managed automatically

    // Helper methods

    private String extractClientId(Map<String, Object> data) {
        return (String) data.get("client_id");
    }

    private BigDecimal extractBalance(Map<String, Object> data) {
        Object balanceObj = data.get("balance");
        if (balanceObj == null) {
            return null;
        }
        try {
            return new BigDecimal(balanceObj.toString());
        } catch (NumberFormatException e) {
            logger.warn("Invalid balance format: {}", balanceObj);
            return null;
        }
    }

    private BalanceUpdateEvent createBalanceUpdateEvent(Map<String, Object> data,
                                                        String clientId,
                                                        BigDecimal newBalance) {
        BalanceUpdateEvent event = new BalanceUpdateEvent();
        event.setClientId(clientId);
        event.setAccountId((String) data.get("account_id"));
        event.setNewBalance(newBalance);
        event.setTimestamp(LocalDateTime.now());

        String source = (String) data.get("source");
        event.setSource(source != null ? source : RiskConstants.SOURCE_WEBSOCKET);

        return event;
    }
}
