package com.orgmemory.graphrag.postgres;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(PostgresGraphRagAutoConfiguration.class))
            .withUserConfiguration(CollaboratorConfiguration.class);

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
    void leavesEveryPortUnclaimedWhenTurnedOff() {
        runner.withPropertyValues("orgmemory.graph-rag.postgres.enabled=false")
                .run(context -> assertTrue(
                        context.getBeansOfType(GraphStore.class).isEmpty()
                                && context.getBeansOfType(ContentStore.class).isEmpty(),
                        "disabling the adapter must not leave a half-wired store"));
    }

    private static List<String> registeredAutoConfigurations() {
        var names = new ArrayList<String>();
        ImportCandidates.load(
                        AutoConfiguration.class,
                        PostgresGraphRagAutoConfigurationTests.class.getClassLoader())
                .forEach(names::add);
        return names;
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
