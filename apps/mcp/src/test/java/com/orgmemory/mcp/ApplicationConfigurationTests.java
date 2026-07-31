package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
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

    @Test
    void productionOverridesOnlyItsDeclaredProperties() throws IOException {
        PropertySource<?> production = load("application-prod.yml");
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(production);
        sources.addLast(load("application.yml"));
        var properties = new PropertySourcesPropertyResolver(sources);

        assertEquals(
                "assets:read",
                properties.getProperty(
                        "spring.security.oauth2.client.registration.orgmemory-api.scope"));
        assertEquals(
                "75s",
                properties.getProperty("orgmemory.mcp.request-timeout"));
        assertEquals(
                "${ORGMEMORY_API_BASE_URL}",
                production.getProperty("orgmemory.mcp.api-base-url"));
    }

    private static void assertSingleYamlDocument(String path) throws IOException {
        List<PropertySource<?>> sources = loadAll(path);

        assertEquals(
                1,
                sources.size(),
                () -> path + " must contain exactly one valid YAML document");
    }

    private static PropertySource<?> load(String path) throws IOException {
        return loadAll(path).getFirst();
    }

    private static List<PropertySource<?>> loadAll(String path) throws IOException {
        Resource resource = new ClassPathResource(path);
        return new YamlPropertySourceLoader().load(resource.getFilename(), resource);
    }
}
