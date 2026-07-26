package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KnowledgeSearchApiClientTests {

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void forwardsTheExchangedApiBearerToTheCanonicalSearchApi() {
        server.expect(requestTo(
                        "https://api.example.test/api/knowledge/search"
                                + "?q=expense%20policy&limit=5"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer api-token"))
                .andRespond(withSuccess(
                        """
                        {
                          "requestId": "request-1",
                          "evidence": [{
                            "citationId": "40000000-0000-0000-0000-000000000001",
                            "knowledgeAssetId": "10000000-0000-0000-0000-000000000001",
                            "title": "Expense policy",
                            "content": "Submit receipts within 30 days.",
                            "sourceUri": "minio://opaque",
                            "startPage": 2,
                            "endPage": 2,
                            "heading": "Claims",
                            "relevanceScore": 0.92
                          }]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        var result = client().search(
                "Bearer api-token",
                "expense policy",
                5);

        assertEquals("request-1", result.requestId());
        assertEquals(1, result.evidence().size());
        assertEquals(
                "Submit receipts within 30 days.",
                result.evidence().getFirst().content());
        server.verify();
    }

    @Test
    void mapsAuthorizationRefusalsWithoutLeakingTheApiBody() {
        server.expect(requestTo(
                        "https://api.example.test/api/knowledge/search"
                                + "?q=forecast"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("internal-policy-detail")
                        .contentType(MediaType.TEXT_PLAIN));

        var refused = assertThrows(
                KnowledgeSearchApiClient.KnowledgeSearchGatewayException.class,
                () -> client().search(
                        "Bearer api-token",
                        "forecast",
                        null));

        assertFalse(refused.getMessage().contains(
                "internal-policy-detail"));
        server.verify();
    }

    @Test
    void hidesSearchTransportFailureDetails() {
        server.expect(requestTo(
                        "https://api.example.test/api/knowledge/search"
                                + "?q=expense"))
                .andRespond(withException(
                        new IOException("private network topology")));

        var failure = assertThrows(
                KnowledgeSearchApiClient.KnowledgeSearchGatewayException.class,
                () -> client().search(
                        "Bearer api-token",
                        "expense",
                        null));

        assertEquals(
                "OrgMemory knowledge search is temporarily unavailable",
                failure.getMessage());
        assertNotNull(failure.getCause());
        server.verify();
    }

    private KnowledgeSearchApiClient client() {
        return new KnowledgeSearchApiClient(
                builder,
                new McpGatewayProperties(
                        URI.create("https://api.example.test"),
                        Duration.ofSeconds(5),
                        URI.create("https://mcp.example.test/mcp"),
                        URI.create("https://id.example.test/realms/orgmemory"),
                        URI.create("https://id.example.test/realms/orgmemory/protocol/openid-connect/certs"),
                        "https://mcp.example.test/mcp",
                        "orgmemory-web"));
    }
}
