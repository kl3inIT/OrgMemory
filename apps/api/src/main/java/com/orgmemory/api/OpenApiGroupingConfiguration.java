package com.orgmemory.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps the browser product contract and the machine SCIM protocol contract
 * independently versioned. A SCIM operation must never enter the generated
 * browser client merely because both adapters run in the same process.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiGroupingConfiguration {

    @Bean
    GroupedOpenApi productOpenApi() {
        return GroupedOpenApi.builder()
                .group("product")
                .pathsToMatch("/api/**")
                .build();
    }

    @Bean
    GroupedOpenApi scimOpenApi() {
        return GroupedOpenApi.builder()
                .group("scim")
                .pathsToMatch("/scim/v2/**")
                .addOpenApiCustomizer(openApi -> {
                    if (openApi.getComponents() == null) {
                        openApi.setComponents(new Components());
                    }
                    openApi.getComponents().addSecuritySchemes(
                            "scimBearer",
                            new SecurityScheme()
                                    .type(SecurityScheme.Type.HTTP)
                                    .scheme("bearer")
                                    .bearerFormat("OrgMemory SCIM connection token"));
                })
                .build();
    }
}
