package com.orgmemory.mcp;

import java.net.http.HttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class KnowledgeSearchHttpConfiguration {

    /**
     * Builds the client through Spring Boot's configurer rather than from a bare
     * {@code RestClient.builder()}.
     *
     * <p>A bare builder carries no {@code RestClientCustomizer}, and the observation customizer
     * is what writes {@code traceparent} onto an outgoing request. Without it this process
     * opened a trace, called the API, and the API opened a second unrelated one — the calls were
     * recorded, they just could not be joined, which looks like working telemetry until someone
     * tries to follow a request across the hop.
     *
     * <p>{@code configure} sets a request factory of its own before applying the customizers, so
     * the gateway's factory is applied afterwards: the timeouts here are the deployment's and
     * must win, while the customizers are additive and survive the replacement.
     */
    @Bean("orgMemoryApiRestClientBuilder")
    RestClient.Builder orgMemoryApiRestClientBuilder(
            McpGatewayProperties properties,
            ObjectProvider<RestClientBuilderConfigurer> configurers) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.requestTimeout());

        RestClient.Builder builder = RestClient.builder();
        RestClientBuilderConfigurer configurer = configurers.getIfAvailable();
        if (configurer != null) {
            configurer.configure(builder);
        }
        return builder.requestFactory(requestFactory);
    }
}
