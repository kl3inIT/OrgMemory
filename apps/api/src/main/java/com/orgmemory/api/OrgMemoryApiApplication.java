package com.orgmemory.api;

import com.orgmemory.core.knowledge.SourceIngestionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "com.orgmemory.api",
        "com.orgmemory.core",
        "com.orgmemory.integrations.authorization.openfga"
})
@EntityScan("com.orgmemory.core")
@EnableJpaRepositories("com.orgmemory.core")
@EnableConfigurationProperties(SourceIngestionProperties.class)
public class OrgMemoryApiApplication {

    static void main(String[] args) {
        SpringApplication.run(OrgMemoryApiApplication.class, args);
    }
}
