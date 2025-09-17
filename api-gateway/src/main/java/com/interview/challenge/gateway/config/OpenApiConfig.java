package com.interview.challenge.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Bean
    @Profile("local")
    public OpenAPI openAPILocal() {
        return createOpenAPI("http://localhost:" + serverPort);
    }

    @Bean
    @Profile("docker")
    public OpenAPI openAPIDocker() {
        return createOpenAPI("http://localhost:8080");
    }

    @Bean
    @Profile("production")
    public OpenAPI openAPIProduction() {
        return createOpenAPI("https://api.trading-platform.com");
    }

    private OpenAPI createOpenAPI(String serverUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("API Gateway - Trading Risk Management System")
                        .version("1.0.0")
                        .description("Central API Gateway for all microservices in the Trading Risk Management System")
                        .termsOfService("https://trading-platform.com/terms")
                        .contact(new Contact()
                                .name("Trading Platform Support")
                                .url("https://trading-platform.com")
                                .email("support@trading-platform.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server()
                                .url(serverUrl)
                                .description("API Gateway Server (" + activeProfile + ")")
                ))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .bearerFormat("JWT")
                                        .scheme("bearer")
                                        .description("JWT token authentication")));
    }
}