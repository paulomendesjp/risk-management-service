package com.interview.challenge.shared.client;

import com.interview.challenge.shared.model.ClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 👤 USER SERVICE CLIENT
 * 
 * Feign client for communicating with the User Service
 */
@FeignClient(name = "user-service", url = "${user.service.url:http://localhost:8081}")
public interface UserServiceClient {
    
    /**
     * Get client configuration including API credentials and risk limits
     */
    @GetMapping("/api/users/{clientId}")
    ClientConfiguration getClientConfiguration(@PathVariable("clientId") String clientId);
    
    /**
     * Check if client can trade (not blocked)
     */
    @GetMapping("/api/users/{clientId}/can-trade")
    Boolean canClientTrade(@PathVariable("clientId") String clientId);
    
    /**
     * Block client trading (daily or permanent)
     */
    @PostMapping("/api/users/{clientId}/block")
    void blockClient(@PathVariable("clientId") String clientId, @RequestParam("permanent") boolean permanent);

    /**
     * Get decrypted API credentials for a client
     */
    @GetMapping("/api/users/{clientId}/credentials")
    java.util.Map<String, String> getDecryptedCredentials(@PathVariable("clientId") String clientId);

    /**
     * Get all clients
     */
    @GetMapping("/api/users")
    java.util.List<ClientConfiguration> getAllClients();
}
