package com.interview.challenge.shared.client;

import com.interview.challenge.shared.dto.ClosePositionsResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 📊 POSITION SERVICE CLIENT
 * 
 * Feign client for communicating with the Position Service
 */
@FeignClient(name = "position-service", url = "${position.service.url:http://localhost:8082}")
public interface PositionServiceClient {
    
    /**
     * Close all open positions for a client due to risk violation
     */
    @PostMapping("/api/positions/{clientId}/close-all")
    ClosePositionsResult closeAllPositions(
        @PathVariable("clientId") String clientId,
        @RequestParam("reason") String reason
    );
    
    /**
     * Get all open positions for a client
     */
    @PostMapping("/api/positions/{clientId}/open")
    List<Map<String, Object>> getOpenPositions(@PathVariable("clientId") String clientId);
    
    /**
     * Emergency stop - cancel all pending orders for a client
     */
    @PostMapping("/api/positions/{clientId}/emergency-stop")
    Map<String, Object> emergencyStop(
        @PathVariable("clientId") String clientId,
        @RequestParam("reason") String reason
    );
}
