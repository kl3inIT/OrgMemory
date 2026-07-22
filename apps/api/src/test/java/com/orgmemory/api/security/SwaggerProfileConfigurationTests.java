package com.orgmemory.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class SwaggerProfileConfigurationTests {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void swaggerIsDisabledByDefault() throws IOException {
        var properties = loader.load("application", new ClassPathResource("application.yml")).getFirst();

        assertEquals(false, properties.getProperty("springdoc.api-docs.enabled"));
        assertEquals(false, properties.getProperty("springdoc.swagger-ui.enabled"));
    }

    @Test
    void swaggerIsEnabledOnlyByTheDevelopmentProfile() throws IOException {
        var properties = loader.load("application-dev", new ClassPathResource("application-dev.yml")).getFirst();

        assertEquals(true, properties.getProperty("springdoc.api-docs.enabled"));
        assertEquals(true, properties.getProperty("springdoc.swagger-ui.enabled"));
    }
}
