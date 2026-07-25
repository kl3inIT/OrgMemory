package com.orgmemory.graphrag.neo4j;

import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import java.util.concurrent.TimeUnit;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(
        beforeName = {
            "com.orgmemory.graphrag.opensearch.OpenSearchGraphRagAutoConfiguration",
            "com.orgmemory.graphrag.postgres.PostgresGraphRagAutoConfiguration"
        })
@ConditionalOnClass(Driver.class)
@ConditionalOnProperty(
        prefix = "orgmemory.graph-rag.neo4j",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(Neo4jGraphRagProperties.class)
public class Neo4jGraphRagAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    Driver neo4jGraphRagDriver(Neo4jGraphRagProperties properties) {
        properties.validate();
        Config config = Config.builder()
                .withMaxConnectionPoolSize(properties.getMaximumConnectionPoolSize())
                .withConnectionTimeout(
                        properties.getConnectionTimeout().toMillis(),
                        TimeUnit.MILLISECONDS)
                .withConnectionAcquisitionTimeout(
                        properties.getConnectionAcquisitionTimeout().toMillis(),
                        TimeUnit.MILLISECONDS)
                .withMaxTransactionRetryTime(
                        properties.getMaximumTransactionRetryTime().toMillis(),
                        TimeUnit.MILLISECONDS)
                .withFetchSize(properties.getFetchSize())
                .withUserAgent("orgmemory-graph-rag-neo4j/1")
                .withTelemetryDisabled(true)
                .build();
        Driver driver = GraphDatabase.driver(
                properties.getUri(),
                AuthTokens.basic(properties.getUsername(), properties.getPassword()),
                config);
        try {
            driver.verifyConnectivity();
            return driver;
        } catch (RuntimeException exception) {
            driver.close();
            throw exception;
        }
    }

    @Bean
    @ConditionalOnMissingBean
    Neo4jOperations neo4jGraphRagOperations(
            Driver driver,
            Neo4jGraphRagProperties properties) {
        return new Neo4jOperations(
                driver,
                properties.getDatabase(),
                properties.getQueryTimeout());
    }

    @Bean
    @ConditionalOnMissingBean(GraphStore.class)
    GraphStore neo4jGraphStore(
            Neo4jOperations operations,
            ProjectionPublicationStore publications,
            Neo4jGraphRagProperties properties) {
        new Neo4jSchemaInitializer(operations).initialize();
        return new Neo4jGraphStore(operations, publications, properties);
    }
}
