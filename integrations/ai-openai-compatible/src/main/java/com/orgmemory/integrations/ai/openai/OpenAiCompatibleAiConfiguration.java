package com.orgmemory.integrations.ai.openai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiGatewayProperties.class)
public class OpenAiCompatibleAiConfiguration {
}
