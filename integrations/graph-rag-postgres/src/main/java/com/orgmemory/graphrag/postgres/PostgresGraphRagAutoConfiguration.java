package com.orgmemory.graphrag.postgres;

import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
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
    @ConditionalOnMissingBean
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
}
