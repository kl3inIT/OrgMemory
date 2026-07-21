package com.orgmemory.worker;

import com.orgmemory.core.knowledge.SourceIngestionProperties;
import com.orgmemory.core.knowledge.EmbeddingProfileRegistry;
import com.orgmemory.core.knowledge.KnowledgeChunkProjectionStore;
import com.orgmemory.core.knowledge.KnowledgeIngestionService;
import com.orgmemory.core.knowledge.SourceIngestionCoordinator;
import com.orgmemory.core.permission.KnowledgePermissionPolicy;
import com.orgmemory.core.shared.JpaAuditingConfig;
import com.orgmemory.worker.ingestion.SourceProcessingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EntityScan("com.orgmemory.core")
@EnableJpaRepositories("com.orgmemory.core")
@EnableConfigurationProperties({SourceIngestionProperties.class, SourceProcessingProperties.class})
@Import({
        EmbeddingProfileRegistry.class,
        KnowledgeChunkProjectionStore.class,
        KnowledgeIngestionService.class,
        KnowledgePermissionPolicy.class,
        JpaAuditingConfig.class,
        SourceIngestionCoordinator.class
})
public class OrgMemoryWorkerApplication {

    static void main(String[] args) {
        SpringApplication.run(OrgMemoryWorkerApplication.class, args);
    }
}
