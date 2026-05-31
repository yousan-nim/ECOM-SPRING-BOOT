package com.ecom.payment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${app.version:0.1.0}") private String appVersion;

    @Bean
    public OpenAPI paymentOpenAPI() {
        final String bearer = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("ECOM Payment Service")
                        .description("Payments, refunds, vendor payouts, gateway webhooks.")
                        .version(appVersion)
                        .contact(new Contact().name("ECOM Team").email("dev@ecom.dev")))
                .servers(List.of(
                        new Server().url("http://localhost:8083").description("Local (direct)"),
                        new Server().url("http://localhost:8080").description("Via gateway")))
                .addSecurityItem(new SecurityRequirement().addList(bearer))
                .components(new Components().addSecuritySchemes(bearer,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
