package com.orgmemory.graphrag.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

final class OpenSearchTestClient implements AutoCloseable {

    private final OpenSearchTransport transport;
    private final OpenSearchClient client;

    OpenSearchTestClient(String host, int port) {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        transport = ApacheHttpClient5TransportBuilder
                .builder(new HttpHost("http", host, port))
                .setMapper(new JacksonJsonpMapper(mapper))
                .build();
        client = new OpenSearchClient(transport);
    }

    OpenSearchClient client() {
        return client;
    }

    @Override
    public void close() throws IOException {
        transport.close();
    }
}
