package com.orgmemory.integrations.ai.gateway;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiGatewayProperties.class)
public class AiModelGatewayConfiguration {
}
