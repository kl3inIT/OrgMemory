package com.orgmemory.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.orgmemory.api", "com.orgmemory.core"})
@EntityScan("com.orgmemory.core")
@EnableJpaRepositories("com.orgmemory.core")
public class OrgMemoryApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrgMemoryApiApplication.class, args);
    }
}
