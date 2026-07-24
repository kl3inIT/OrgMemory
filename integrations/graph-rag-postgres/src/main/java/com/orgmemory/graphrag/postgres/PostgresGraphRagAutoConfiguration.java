package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.curation.GraphCurationStore;
import com.orgmemory.graphrag.export.GraphExportReader;
import com.orgmemory.graphrag.port.GraphProjectionReader;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration(
        afterName = {
            "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
        })
@ConditionalOnClass({NamedParameterJdbcTemplate.class, PlatformTransactionManager.class})
@ConditionalOnSingleCandidate(NamedParameterJdbcTemplate.class)
@ConditionalOnProperty(
        prefix = "orgmemory.graph-rag.postgres",
        name = "enabled",
        matchIfMissing = true)
@EnableConfigurationProperties(PostgresGraphRagProperties.class)
public class PostgresGraphRagAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GraphProjectionReader.class)
    @DependsOnDatabaseInitialization
    PostgresGraphProjectionStore postgresGraphProjectionStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PostgresGraphRagProperties properties,
            ObjectProvider<Clock> clockProvider) {
        return new PostgresGraphProjectionStore(
                jdbc,
                transactionManager,
                clockProvider.getIfAvailable(Clock::systemUTC),
                properties.toStoreOptions());
    }

    @Bean
    @DependsOnDatabaseInitialization
    @ConditionalOnMissingBean(ModelInvocationCache.class)
    ModelInvocationCache postgresModelInvocationCache(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        return new PostgresModelInvocationCache(jdbc, transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean(RetrievalResultCache.class)
    @DependsOnDatabaseInitialization
    RetrievalResultCache postgresRetrievalResultCache(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        return new PostgresRetrievalResultCache(jdbc, transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean(GraphCurationStore.class)
    @DependsOnDatabaseInitialization
    PostgresGraphCurationStore postgresGraphCurationStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        return new PostgresGraphCurationStore(jdbc, transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean(GraphExportReader.class)
    @DependsOnDatabaseInitialization
    PostgresGraphExportReader postgresGraphExportReader(
            NamedParameterJdbcTemplate jdbc,
            GraphProjectionReader projections,
            GraphCurationStore curations) {
        return new PostgresGraphExportReader(jdbc, projections, curations);
    }

    @Bean
    @ConditionalOnMissingBean
    PostgresGraphVectorIndexManager postgresGraphVectorIndexManager(
            NamedParameterJdbcTemplate jdbc) {
        return new PostgresGraphVectorIndexManager(jdbc.getJdbcTemplate());
    }

    @Bean
    @DependsOnDatabaseInitialization
    ApplicationRunner postgresGraphVectorIndexProvisioner(
            PostgresGraphVectorIndexManager indexManager,
            PostgresGraphRagProperties properties) {
        return arguments -> indexManager.ensureConfiguredIndexes(properties.toStoreOptions());
    }
}
