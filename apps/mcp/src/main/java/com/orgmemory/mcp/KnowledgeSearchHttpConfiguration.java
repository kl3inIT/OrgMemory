package com.orgmemory.mcp;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class KnowledgeSearchHttpConfiguration {

    @Bean("orgMemoryApiRestClientBuilder")
    RestClient.Builder orgMemoryApiRestClientBuilder(
            McpGatewayProperties properties) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .build();
        var requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.requestTimeout());
        return RestClient.builder().requestFactory(requestFactory);
    }
}
