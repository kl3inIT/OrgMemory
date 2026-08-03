package com.orgmemory.graphrag.postgres;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.LexicalIndex;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.VectorIndex;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Every other test in this module builds an adapter directly, so none of them
 * notices when Spring stops loading the module at all. The registration file is
 * plain text that no compiler checks, and the ports are contributed under
 * conditions that a property or an ordering change can quietly invert. These
 * tests fail in that case instead of leaving it for production to discover.
 */
class PostgresGraphRagAutoConfigurationTests {

    /** Every port this adapter contributes outside a servlet application. */
    private static final List<Class<?>> CANONICAL_PORTS = List.of(
            ProjectionPublicationStore.class,
            ContentStore.class,
            GraphStore.class,
            LexicalIndex.class,
            VectorIndex.class,
            ModelInvocationCache.class,
            RetrievalResultCache.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(PostgresGraphRagAutoConfiguration.class))
            .withUserConfiguration(CollaboratorConfiguration.class)
            .withPropertyValues(
                    "orgmemory.graph-rag.postgres.topology-backend=relational");

    @Test
    void staysDiscoverableWithoutAnApplicationNamingIt() {
        assertTrue(
                registeredAutoConfigurations()
                        .contains(PostgresGraphRagAutoConfiguration.class.getName()),
                "META-INF/spring/…AutoConfiguration.imports no longer names this class, "
                        + "so Spring Boot will never load the PostgreSQL adapter");
    }

    @Test
    void ownsTheCanonicalPortsWithoutAnyOptIn() {
        runner.run(context -> {
            assertInstanceOf(
                    PostgresProjectionPublicationStore.class,
                    context.getBean(ProjectionPublicationStore.class));
            assertInstanceOf(PostgresContentStore.class, context.getBean(ContentStore.class));
            assertInstanceOf(PostgresGraphStore.class, context.getBean(GraphStore.class));
            assertInstanceOf(PostgresLexicalIndex.class, context.getBean(LexicalIndex.class));
            assertInstanceOf(PostgresVectorIndex.class, context.getBean(VectorIndex.class));
            assertInstanceOf(
                    PostgresModelInvocationCache.class,
                    context.getBean(ModelInvocationCache.class));
            assertInstanceOf(
                    PostgresRetrievalResultCache.class,
                    context.getBean(RetrievalResultCache.class));
        });
    }

    @Test
    void apacheAgeSelectionFailsClosedWhenTheExtensionIsUnavailable() {
        runner.withPropertyValues(
                        "orgmemory.graph-rag.postgres.topology-backend=apache-age")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertTrue(failure != null);
                    assertTrue(rootCause(failure)
                            .getMessage()
                            .contains("Apache AGE topology backend was selected"));
                });
    }

    @Test
    void defaultSelectionConstructsTheRealApacheAgeRuntimeWhenAvailable() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate plainJdbc = mock(JdbcTemplate.class);
        when(jdbc.getJdbcTemplate()).thenReturn(plainJdbc);
        when(jdbc.queryForObject(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class),
                        org.mockito.ArgumentMatchers.eq(Boolean.class)))
                .thenReturn(true);
        when(plainJdbc.queryForObject(
                        org.mockito.ArgumentMatchers.contains("ag_catalog.ag_graph"),
                        org.mockito.ArgumentMatchers.eq(Long.class)))
                .thenReturn(0L);
        PostgresGraphRagProperties properties = new PostgresGraphRagProperties();

        GraphStore selected = new PostgresGraphRagAutoConfiguration()
                .postgresSharedSnapshotGraphStore(
                        jdbc,
                        mock(PlatformTransactionManager.class),
                        mock(PostgresProjectionPublicationStore.class),
                        properties);

        assertEquals(
                PostgresGraphTopologyBackend.APACHE_AGE,
                properties.getTopologyBackend());
        assertInstanceOf(ApacheAgeGraphStore.class, selected);
    }

    @Test
    void rejectsTheObsoleteApacheAgeModeProperty() {
        runner.withPropertyValues(
                        "orgmemory.graph-rag.postgres.apache-age-mode=disabled")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertTrue(failure != null);
                    assertTrue(failure.getMessage().contains("PostgresGraphRagProperties"));
                });
    }

    @Test
    void rejectsReconciliationAgainstTheRelationalBackend() {
        runner.withPropertyValues(
                        "orgmemory.graph-rag.postgres.reconcile-published-batches=true")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertTrue(failure != null);
                    assertTrue(rootCause(failure)
                            .getMessage()
                            .contains("requires topology-backend=APACHE_AGE"));
                });
    }

    @Test
    void bindsBoundedReconciliationLimits() {
        runner.withPropertyValues(
                        "orgmemory.graph-rag.postgres.reconciliation-page-size=17",
                        "orgmemory.graph-rag.postgres.reconciliation-maximum-batches=23",
                        "orgmemory.graph-rag.postgres.reconciliation-maximum-entities=29",
                        "orgmemory.graph-rag.postgres.reconciliation-maximum-relation-contributions=31")
                .run(context -> {
                    PostgresGraphRagProperties properties =
                            context.getBean(PostgresGraphRagProperties.class);
                    assertEquals(17, properties.getReconciliationPageSize());
                    assertEquals(23, properties.getReconciliationMaximumBatches());
                    assertEquals(29, properties.getReconciliationMaximumEntities());
                    assertEquals(
                            31,
                            properties.getReconciliationMaximumRelationContributions());
                });
    }

    @Test
    void leavesEveryPortUnclaimedWhenTurnedOff() {
        runner.withPropertyValues("orgmemory.graph-rag.postgres.enabled=false")
                .run(context -> CANONICAL_PORTS.forEach(port -> assertTrue(
                        context.getBeansOfType(port).isEmpty(),
                        "disabling the adapter must not leave %s wired"
                                .formatted(port.getSimpleName()))));
    }

    @Test
    void sharedDevelopmentCanDisableIndexProvisioning() throws Exception {
        var manager = mock(PostgresGraphVectorIndexManager.class);
        var properties = new PostgresGraphRagProperties();
        properties.setProvisionIndexes(false);

        new PostgresGraphRagAutoConfiguration()
                .postgresGraphVectorIndexProvisioner(manager, properties)
                .run(mock(ApplicationArguments.class));

        verifyNoInteractions(manager);
    }

    @Test
    void bindsTheBoundedWriteBatchSize() {
        runner.withPropertyValues("orgmemory.graph-rag.postgres.write-batch-size=37")
                .run(context -> assertEquals(
                        37,
                        context.getBean(PostgresGraphRagProperties.class)
                                .getWriteBatchSize()));
    }

    private static List<String> registeredAutoConfigurations() {
        var names = new ArrayList<String>();
        ImportCandidates.load(
                        AutoConfiguration.class,
                        PostgresGraphRagAutoConfigurationTests.class.getClassLoader())
                .forEach(names::add);
        return names;
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /** The collaborators an application already owns before this adapter loads. */
    @Configuration(proxyBeanMethods = false)
    static class CollaboratorConfiguration {

        @Bean
        NamedParameterJdbcTemplate namedParameterJdbcTemplate() {
            return mock(NamedParameterJdbcTemplate.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }
    }
}
