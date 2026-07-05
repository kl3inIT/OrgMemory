package com.orgmemory.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.orgmemory.worker", "com.orgmemory.core"})
@EntityScan("com.orgmemory.core")
@EnableJpaRepositories("com.orgmemory.core")
public class OrgMemoryWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrgMemoryWorkerApplication.class, args);
    }
}
