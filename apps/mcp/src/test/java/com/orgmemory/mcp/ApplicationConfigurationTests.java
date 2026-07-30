package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Guards the configuration files that are packaged into the MCP image.
 *
 * <p>The production profile is not activated by the regular context test. A duplicate top-level
 * key in {@code application-prod.yml} therefore reached an immutable image and stopped the
 * process before its readiness probe could become healthy.
 */
class ApplicationConfigurationTests {

    @Test
    void baseConfigurationIsOneValidYamlDocument() throws IOException {
        assertSingleYamlDocument("application.yml");
    }

    @Test
    void productionConfigurationIsOneValidYamlDocument() throws IOException {
        assertSingleYamlDocument("application-prod.yml");
    }

    private static void assertSingleYamlDocument(String path) throws IOException {
        Resource resource = new ClassPathResource(path);
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load(resource.getFilename(), resource);

        assertEquals(
                1,
                sources.size(),
                () -> path + " must contain exactly one valid YAML document");
    }
}
