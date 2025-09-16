package com.interview.challenge.position.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger Configuration for Position Service
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8082}")
    private String serverPort;

    @Bean
    public OpenAPI positionServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Position Service API")
                        .description("Trading Position Management Service - Real Architect.co Integration")
                        .version("3.0.0")
                        .contact(new Contact()
                                .name("Trading Team")
                                .email("trading@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("http://springdoc.org")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server"),
                        new Server()
                                .url("http://position-service:" + serverPort)
                                .description("Docker Container")
                ));
    }
}