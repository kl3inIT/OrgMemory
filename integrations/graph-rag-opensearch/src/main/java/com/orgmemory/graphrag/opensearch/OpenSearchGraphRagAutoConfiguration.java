package com.orgmemory.graphrag.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.LexicalIndex;
import com.orgmemory.graphrag.storage.ProcessingStatusIndex;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.VectorIndex;
import java.net.URI;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(
        beforeName =
                "com.orgmemory.graphrag.postgres.PostgresGraphRagAutoConfiguration")
@ConditionalOnClass(OpenSearchClient.class)
@ConditionalOnProperty(
        prefix = "orgmemory.graph-rag.opensearch",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(OpenSearchGraphRagProperties.class)
public class OpenSearchGraphRagAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean({OpenSearchTransport.class, OpenSearchClient.class})
    OpenSearchTransport openSearchTransport(
            OpenSearchGraphRagProperties properties,
            ObjectMapper objectMapper) {
        properties.validate();
        URI endpoint = properties.getEndpoint();
        int port = endpoint.getPort() > 0
                ? endpoint.getPort()
                : "https".equals(endpoint.getScheme()) ? 443 : 80;
        HttpHost host = new HttpHost(endpoint.getScheme(), endpoint.getHost(), port);
        var builder = ApacheHttpClient5TransportBuilder.builder(host)
                .setMapper(new JacksonJsonpMapper(objectMapper.copy()))
                .setConnectionConfigCallback(connection -> connection
                        .setConnectTimeout(Timeout.ofMilliseconds(
                                properties.getConnectTimeout().toMillis()))
                        .setSocketTimeout(Timeout.ofMilliseconds(
                                properties.getSocketTimeout().toMillis())));
        String path = endpoint.getPath();
        if (path != null && !path.isBlank() && !"/".equals(path)) {
            builder.setPathPrefix(path);
        }
        if (!properties.getUsername().isBlank()) {
            builder.setHttpClientConfigCallback(http -> {
                BasicCredentialsProvider credentials = new BasicCredentialsProvider();
                credentials.setCredentials(
                        new AuthScope(host),
                        new UsernamePasswordCredentials(
                                properties.getUsername(),
                                properties.getPassword().toCharArray()));
                return http.setDefaultCredentialsProvider(credentials);
            });
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    OpenSearchClient openSearchClient(OpenSearchTransport transport) {
        return new OpenSearchClient(transport);
    }

    @Bean
    @ConditionalOnMissingBean
    OpenSearchIndexNames openSearchIndexNames(
            OpenSearchGraphRagProperties properties) {
        return new OpenSearchIndexNames(properties.getIndexPrefix());
    }

    @Bean
    @ConditionalOnMissingBean
    OpenSearchOperations openSearchOperations(
            OpenSearchClient client,
            OpenSearchGraphRagProperties properties) {
        return new OpenSearchOperations(
                client,
                properties.getBulkMaximumOperations());
    }

    @Bean
    @ConditionalOnMissingBean(ProjectionPublicationStore.class)
    OpenSearchProjectionPublicationStore openSearchProjectionPublicationStore(
            OpenSearchOperations operations,
            OpenSearchIndexNames indexes) {
        return new OpenSearchProjectionPublicationStore(operations, indexes);
    }

    @Bean
    @ConditionalOnMissingBean(ContentStore.class)
    OpenSearchContentStore openSearchContentStore(
            OpenSearchOperations operations,
            OpenSearchProjectionPublicationStore publications,
            OpenSearchIndexNames indexes) {
        return new OpenSearchContentStore(operations, publications, indexes);
    }

    @Bean
    @ConditionalOnMissingBean(LexicalIndex.class)
    OpenSearchLexicalIndex openSearchLexicalIndex(
            OpenSearchOperations operations,
            OpenSearchProjectionPublicationStore publications,
            OpenSearchIndexNames indexes) {
        return new OpenSearchLexicalIndex(operations, publications, indexes);
    }

    @Bean
    @ConditionalOnMissingBean(VectorIndex.class)
    OpenSearchVectorIndex openSearchVectorIndex(
            OpenSearchOperations operations,
            OpenSearchProjectionPublicationStore publications,
            OpenSearchIndexNames indexes) {
        return new OpenSearchVectorIndex(operations, publications, indexes);
    }

    @Bean
    @ConditionalOnMissingBean(GraphStore.class)
    OpenSearchGraphStore openSearchGraphStore(
            OpenSearchOperations operations,
            OpenSearchProjectionPublicationStore publications,
            OpenSearchIndexNames indexes,
            OpenSearchGraphRagProperties properties,
            ObjectMapper objectMapper) {
        OpenSearchPplGraphLookup ppl = new OpenSearchPplGraphLookup(
                operations,
                indexes,
                objectMapper,
                properties.isPplGraphLookupEnabled());
        return new OpenSearchGraphStore(
                operations,
                publications,
                indexes,
                properties.getGraphMaximumFrontier(),
                ppl);
    }

    @Bean
    @ConditionalOnMissingBean(ProcessingStatusIndex.class)
    OpenSearchProcessingStatusIndex openSearchProcessingStatusIndex(
            OpenSearchOperations operations,
            OpenSearchIndexNames indexes) {
        return new OpenSearchProcessingStatusIndex(operations, indexes);
    }
}
