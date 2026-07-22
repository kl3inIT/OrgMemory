package com.orgmemory.worker;

import com.orgmemory.core.knowledge.SecureKnowledgeRetrievalService;
import com.orgmemory.core.knowledge.SourceIngestionProperties;
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
@EnableConfigurationProperties({SourceIngestionProperties.class, SourceProcessingProperties.class})
@ComponentScan(
        basePackages = {
                "com.orgmemory.worker",
                "com.orgmemory.core.knowledge",
                "com.orgmemory.core.permission",
                "com.orgmemory.core.shared",
                "com.orgmemory.integrations.ai.openai",
                "com.orgmemory.integrations.authorization.openfga"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = SecureKnowledgeRetrievalService.class))
public class OrgMemoryWorkerApplication {

    static void main(String[] args) {
        SpringApplication.run(OrgMemoryWorkerApplication.class, args);
    }
}
