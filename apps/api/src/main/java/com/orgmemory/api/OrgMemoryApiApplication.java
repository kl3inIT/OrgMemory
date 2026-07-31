package com.orgmemory.api;

import com.orgmemory.core.knowledge.sourceledger.SourceIngestionProperties;

import com.orgmemory.core.knowledge.graph.GraphExplorerProperties;
import com.orgmemory.core.knowledge.graph.GraphProcessingProperties;
import com.orgmemory.core.knowledge.retrieval.KnowledgeEmbeddingProperties;
import com.orgmemory.core.knowledge.KnowledgeRetrievalProperties;
import com.orgmemory.core.shared.secret.SecretCipherProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "com.orgmemory.api",
        "com.orgmemory.core",
        "com.orgmemory.integrations.ai.gateway",
        "com.orgmemory.integrations.authorization.openfga"
})
@EntityScan("com.orgmemory.core")
@EnableJpaRepositories("com.orgmemory.core")
@EnableConfigurationProperties({
        SourceIngestionProperties.class,
        SecretCipherProperties.class,
        KnowledgeRetrievalProperties.class,
        KnowledgeEmbeddingProperties.class,
        GraphExplorerProperties.class,
        GraphProcessingProperties.class
})
public class OrgMemoryApiApplication {

    static void main(String[] args) {
        SpringApplication.run(OrgMemoryApiApplication.class, args);
    }
}
