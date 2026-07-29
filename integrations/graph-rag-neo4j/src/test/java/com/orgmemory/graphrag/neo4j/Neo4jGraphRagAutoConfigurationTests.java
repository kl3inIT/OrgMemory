package com.orgmemory.graphrag.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Neo4j takes the graph port away from whichever adapter would otherwise hold
 * it, so the same two questions matter as for OpenSearch: it must stay inert
 * until an operator enables it, and enabling it must actually produce the
 * store rather than fail somewhere Spring reports as a missing bean.
 */
class Neo4jGraphRagAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Neo4jGraphRagAutoConfiguration.class))
            .withUserConfiguration(CollaboratorConfiguration.class);

    @Test
    void staysDiscoverableWithoutAnApplicationNamingIt() {
        assertTrue(
                registeredAutoConfigurations()
                        .contains(Neo4jGraphRagAutoConfiguration.class.getName()),
                "META-INF/spring/…AutoConfiguration.imports no longer names this class, "
                        + "so enabling the property would silently do nothing");
    }

    @Test
    void claimsNoGraphPortUntilAnOperatorAsksForIt() {
        runner.run(context -> assertTrue(
                context.getBeansOfType(GraphStore.class).isEmpty(),
                "classpath presence must not displace the canonical graph store"));
    }

    @Test
    void contributesTheGraphStoreOnceEnabled() {
        runner.withPropertyValues(
                        "orgmemory.graph-rag.neo4j.enabled=true",
                        "orgmemory.graph-rag.neo4j.password=test-password")
                .run(context -> assertInstanceOf(
                        Neo4jGraphStore.class, context.getBean(GraphStore.class)));
    }

    @Test
    void refusesToStartRatherThanReachNeo4jUnauthenticated() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(Neo4jGraphRagAutoConfiguration.class))
                .withUserConfiguration(PublicationConfiguration.class)
                .withPropertyValues("orgmemory.graph-rag.neo4j.enabled=true")
                .run(context -> {
                    Throwable cause = rootCause(context.getStartupFailure());
                    assertInstanceOf(
                            IllegalArgumentException.class,
                            cause,
                            "an enabled adapter without a password must fail startup");
                    assertEquals(
                            "Neo4j password must be configured when the adapter is enabled",
                            cause.getMessage(),
                            "the password branch must be what fails, not another "
                                    + "validation rule");
                });
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause != null && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static List<String> registeredAutoConfigurations() {
        var names = new ArrayList<String>();
        ImportCandidates.load(
                        AutoConfiguration.class,
                        Neo4jGraphRagAutoConfigurationTests.class.getClassLoader())
                .forEach(names::add);
        return names;
    }

    /**
     * The collaborators an application already owns before this adapter loads.
     * Supplying the driver and {@link Neo4jOperations} keeps a live Neo4j out of
     * a unit test; the adapter backs off both because they are conditional on a
     * missing bean.
     */
    @Configuration(proxyBeanMethods = false)
    static class CollaboratorConfiguration extends PublicationConfiguration {

        @Bean
        Driver neo4jDriver() {
            return mock(Driver.class);
        }

        @Bean
        Neo4jOperations neo4jOperations() {
            return mock(Neo4jOperations.class);
        }
    }

    /** Only the port the adapter expects an application to already provide. */
    @Configuration(proxyBeanMethods = false)
    static class PublicationConfiguration {

        @Bean
        ProjectionPublicationStore projectionPublicationStore() {
            return mock(ProjectionPublicationStore.class);
        }
    }
}
