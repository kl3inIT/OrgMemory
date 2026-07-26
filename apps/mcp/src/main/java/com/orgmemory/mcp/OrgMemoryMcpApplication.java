package com.orgmemory.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    McpGatewayProperties.class,
    McpRateLimitProperties.class
})
public class OrgMemoryMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrgMemoryMcpApplication.class, args);
    }
}
