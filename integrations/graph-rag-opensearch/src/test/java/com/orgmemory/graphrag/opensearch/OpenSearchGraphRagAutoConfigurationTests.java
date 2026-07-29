package com.orgmemory.graphrag.opensearch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.GraphStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * OpenSearch takes the storage ports away from PostgreSQL when it loads, so the
 * property that turns it on is a production-shaped decision rather than a tuning
 * knob. These are the two questions that need no server to answer: Spring can
 * still find the adapter, and the adapter stays out of the way until asked for.
 * {@link OpenSearchProjectionPublicationIntegrationTests} covers what wiring it
 * up actually produces, because these beans reach OpenSearch as they are built.
 */
class OpenSearchGraphRagAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(OpenSearchGraphRagAutoConfiguration.class));

    @Test
    void staysDiscoverableWithoutAnApplicationNamingIt() {
        assertTrue(
                registeredAutoConfigurations()
                        .contains(OpenSearchGraphRagAutoConfiguration.class.getName()),
                "META-INF/spring/…AutoConfiguration.imports no longer names this class, "
                        + "so enabling the property would silently do nothing");
    }

    @Test
    void claimsNoPortUntilAnOperatorAsksForIt() {
        runner.run(context -> assertTrue(
                context.getBeansOfType(GraphStore.class).isEmpty()
                        && context.getBeansOfType(ContentStore.class).isEmpty(),
                "classpath presence must not displace the canonical PostgreSQL adapter"));
    }

    private static List<String> registeredAutoConfigurations() {
        var names = new ArrayList<String>();
        ImportCandidates.load(
                        AutoConfiguration.class,
                        OpenSearchGraphRagAutoConfigurationTests.class.getClassLoader())
                .forEach(names::add);
        return names;
    }
}
