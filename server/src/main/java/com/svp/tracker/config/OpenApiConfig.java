package com.svp.tracker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI/Swagger metadata shown in Swagger UI. */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearer-jwt";

    private final WebProperties webProperties;

    public OpenApiConfig(WebProperties webProperties) {
        this.webProperties = webProperties;
    }

    @Bean
    OpenAPI trackerOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Health Tracker & PFM API")
                                .description(
                                        "REST API for exercise, finance, calendar, management, and admin diagnostics —"
                                                + " Health Tracker & PFM (Personal Financial Management). Use Authorize"
                                                + " with a JWT from POST /api/auth/login to try authenticated routes.")
                                .version("18.0.0")
                                .contact(new Contact().name(ApplicationBranding.DISPLAY_NAME))
                                .license(new License().name("Proprietary")))
                .servers(servers())
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Access token from POST /api/auth/login (field accessToken).")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    private List<Server> servers() {
        List<Server> servers = new ArrayList<>();
        servers.add(new Server().url("/").description("This host (same origin as the UI)"));
        servers.add(new Server().url("http://localhost:9091").description("Local API"));
        String publicUrl = webProperties.publicAppUrl();
        if (publicUrl != null && !publicUrl.isBlank() && !publicUrl.contains("localhost")) {
            servers.add(new Server().url(publicUrl).description("Public app origin"));
        }
        return servers;
    }
}
