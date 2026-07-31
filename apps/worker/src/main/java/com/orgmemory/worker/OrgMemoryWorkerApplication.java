package com.orgmemory.worker;

import com.orgmemory.core.knowledge.sourceledger.SourceIngestionProperties;

import com.orgmemory.core.knowledge.CanonicalHybridKnowledgeSearch;
import com.orgmemory.core.knowledge.graph.GraphProcessingProperties;
import com.orgmemory.core.knowledge.graph.KnowledgeGraphExplorerConfiguration;
import com.orgmemory.core.knowledge.KnowledgeRetrievalProperties;
import com.orgmemory.core.shared.secret.SecretCipherProperties;
import com.orgmemory.worker.authorization.AssetAuthorizationConvergenceProperties;
import com.orgmemory.worker.authorization.KnowledgeAuthorizationConvergenceProperties;
import com.orgmemory.worker.connector.ConnectorCrawlProperties;
import com.orgmemory.worker.graph.GraphIndexingProperties;
import com.orgmemory.worker.ingestion.SourceProcessingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EntityScan("com.orgmemory.core")
@EnableJpaRepositories("com.orgmemory.core")
@EnableConfigurationProperties({
        SourceIngestionProperties.class,
        SecretCipherProperties.class,
        SourceProcessingProperties.class,
        KnowledgeRetrievalProperties.class,
        ConnectorCrawlProperties.class,
        GraphIndexingProperties.class,
        GraphProcessingProperties.class,
        KnowledgeAuthorizationConvergenceProperties.class,
        AssetAuthorizationConvergenceProperties.class
})
@ComponentScan(
        basePackages = {
                "com.orgmemory.worker",
                "com.orgmemory.core.knowledge",
                "com.orgmemory.core.assetregistry",
                "com.orgmemory.core.ai",
                "com.orgmemory.core.permission",
                "com.orgmemory.core.shared",
                "com.orgmemory.integrations.ai.gateway",
                "com.orgmemory.integrations.authorization.openfga"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        CanonicalHybridKnowledgeSearch.class,
                        KnowledgeGraphExplorerConfiguration.class
                }))
public class OrgMemoryWorkerApplication {

    static void main(String[] args) {
        SpringApplication.run(OrgMemoryWorkerApplication.class, args);
    }
}
