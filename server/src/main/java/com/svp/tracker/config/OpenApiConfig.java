package com.svp.tracker.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI/Swagger metadata shown in Swagger UI. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI trackerOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Health Tracker & PFM API")
                                .description(
                                        "REST API for exercise, finance, and admin diagnostics — Health Tracker & PFM"
                                                + " (Personal Financial Management).")
                                .version("v1")
                                .contact(new Contact().name(ApplicationBranding.DISPLAY_NAME))
                                .license(new License().name("Proprietary")));
    }
}
