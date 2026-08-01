package com.orgmemory.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class AiRouteDefaultsTests {

    @Test
    void graphExtractionDoesNotInheritAnExplicitAssistantModel() {
        StandardEnvironment environment = load(Map.of(
                "ORGMEMORY_OPENAI_MODEL", "assistant-override"));

        assertEquals("assistant-override", environment.getProperty(
                "orgmemory.ai.routes.assistant-chat.model-id"));
        assertEquals("assistant-override", environment.getProperty(
                "orgmemory.ai.routes.keyword-planning.model-id"));
        assertEquals("gpt-5.4-mini", environment.getProperty(
                "orgmemory.ai.routes.graph-extraction.model-id"));
    }

    @Test
    void explicitGraphExtractionModelStillWins() {
        StandardEnvironment environment = load(Map.of(
                "ORGMEMORY_OPENAI_MODEL", "assistant-override",
                "ORGMEMORY_GRAPH_EXTRACTION_MODEL", "graph-override"));

        assertEquals("graph-override", environment.getProperty(
                "orgmemory.ai.routes.graph-extraction.model-id"));
    }

    private static StandardEnvironment load(Map<String, Object> overrides) {
        StandardEnvironment environment = new StandardEnvironment();
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load("application.yml", new ClassPathResource("application.yml"));
            sources.forEach(environment.getPropertySources()::addLast);
        } catch (IOException unreadable) {
            throw new IllegalStateException("application.yml is not on the test classpath", unreadable);
        }
        environment.getPropertySources().addFirst(new MapPropertySource("test-overrides", overrides));
        return environment;
    }
}
