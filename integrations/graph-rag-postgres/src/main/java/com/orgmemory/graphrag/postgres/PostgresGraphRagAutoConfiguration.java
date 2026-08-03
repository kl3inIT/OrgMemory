package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.curation.GraphCurationStore;
import com.orgmemory.graphrag.export.GraphExportReader;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.LexicalIndex;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.VectorIndex;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
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
    @ConditionalOnMissingBean(ProjectionPublicationStore.class)
    @DependsOnDatabaseInitialization
    PostgresProjectionPublicationStore postgresProjectionPublicationStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        return new PostgresProjectionPublicationStore(jdbc, transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean(ContentStore.class)
    @DependsOnDatabaseInitialization
    PostgresContentStore postgresContentStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PostgresProjectionPublicationStore publications,
            PostgresGraphRagProperties properties) {
        return new PostgresContentStore(
                jdbc, transactionManager, publications, properties.getWriteBatchSize());
    }

    @Bean
    @ConditionalOnMissingBean(LexicalIndex.class)
    @DependsOnDatabaseInitialization
    PostgresLexicalIndex postgresLexicalIndex(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PostgresProjectionPublicationStore publications,
            PostgresGraphRagProperties properties) {
        return new PostgresLexicalIndex(
                jdbc, transactionManager, publications, properties.getWriteBatchSize());
    }

    @Bean
    @ConditionalOnMissingBean(VectorIndex.class)
    @DependsOnDatabaseInitialization
    PostgresVectorIndex postgresVectorIndex(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PostgresProjectionPublicationStore publications,
            PostgresGraphRagProperties properties) {
        return new PostgresVectorIndex(
                jdbc, transactionManager, publications, properties.getWriteBatchSize());
    }

    @Bean
    @ConditionalOnMissingBean(GraphStore.class)
    @DependsOnDatabaseInitialization
    GraphStore postgresSharedSnapshotGraphStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PostgresProjectionPublicationStore publications,
            PostgresGraphRagProperties properties) {
        return switch (properties.getTopologyBackend()) {
            case RELATIONAL -> new PostgresGraphStore(
                    jdbc,
                    transactionManager,
                    publications,
                    properties.getWriteBatchSize());
            case APACHE_AGE -> new ApacheAgeGraphStore(
                    jdbc,
                    transactionManager,
                    publications,
                    properties.getWriteBatchSize());
        };
    }

    @Bean
    @DependsOnDatabaseInitialization
    @ConditionalOnProperty(
            prefix = "orgmemory.graph-rag.postgres",
            name = "reconcile-published-batches",
            havingValue = "true")
    ApacheAgePublishedBatchReconciler apacheAgePublishedBatchReconciler(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PostgresProjectionPublicationStore publications,
            PostgresGraphRagProperties properties) {
        if (properties.getTopologyBackend() != PostgresGraphTopologyBackend.APACHE_AGE) {
            throw new IllegalStateException(
                    "Published-batch reconciliation requires topology-backend=APACHE_AGE");
        }
        return new ApacheAgePublishedBatchReconciler(
                publications,
                new ApacheAgeBatchTopology(jdbc, properties.getWriteBatchSize()),
                transactionManager);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "orgmemory.graph-rag.postgres",
            name = "reconcile-published-batches",
            havingValue = "true")
    ApplicationRunner apacheAgePublishedBatchReconciliationRunner(
            ApacheAgePublishedBatchReconciler reconciler,
            PostgresGraphRagProperties properties) {
        return arguments -> reconciler.reconcile(properties);
    }

    @Bean
    @DependsOnDatabaseInitialization
    @ConditionalOnMissingBean(ModelInvocationCache.class)
    ModelInvocationCache postgresModelInvocationCache(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PostgresGraphRagProperties properties) {
        return new PostgresModelInvocationCache(
                jdbc, transactionManager, properties.getWriteBatchSize());
    }

    @Bean
    @ConditionalOnMissingBean(RetrievalResultCache.class)
    @DependsOnDatabaseInitialization
    RetrievalResultCache postgresRetrievalResultCache(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PostgresGraphRagProperties properties) {
        return new PostgresRetrievalResultCache(
                jdbc, transactionManager, properties.getWriteBatchSize());
    }

    @Bean
    @ConditionalOnMissingBean(GraphCurationStore.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @DependsOnDatabaseInitialization
    PostgresGraphCurationStore postgresGraphCurationStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        return new PostgresGraphCurationStore(jdbc, transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean(GraphExportReader.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @DependsOnDatabaseInitialization
    PostgresGraphExportReader postgresGraphExportReader(
            NamedParameterJdbcTemplate jdbc,
            GraphStore graphs,
            ProjectionPublicationStore publications,
            GraphCurationStore curations) {
        return new PostgresGraphExportReader(
                jdbc,
                graphs,
                publications,
                curations);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    PostgresGraphVectorIndexManager postgresGraphVectorIndexManager(
            NamedParameterJdbcTemplate jdbc) {
        return new PostgresGraphVectorIndexManager(jdbc.getJdbcTemplate());
    }

    @Bean
    @DependsOnDatabaseInitialization
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    ApplicationRunner postgresGraphVectorIndexProvisioner(
            PostgresGraphVectorIndexManager indexManager,
            PostgresGraphRagProperties properties) {
        return arguments -> {
            if (properties.isProvisionIndexes()) {
                indexManager.ensureConfiguredIndexes(properties.toStoreOptions());
            }
        };
    }
}
