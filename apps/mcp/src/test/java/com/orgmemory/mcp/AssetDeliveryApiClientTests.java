package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AssetDeliveryApiClientTests {

    private static final UUID ASSET_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RELEASE_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void searchesOnlyThroughTheCanonicalScopedDeliveryApi() {
        server.expect(requestTo(
                        "https://api.example.test/api/asset-delivery"
                                + "?q=onboarding&type=CAPABILITY_PACK"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION, "Bearer api-token"))
                .andRespond(withSuccess(
                        """
                        [{
                          "assetId":"10000000-0000-0000-0000-000000000001",
                          "type":"CAPABILITY_PACK",
                          "namespace":"support",
                          "slug":"onboarding",
                          "title":"Support onboarding",
                          "summary":"Pinned support path",
                          "knowledgeSpaceId":"30000000-0000-0000-0000-000000000001",
                          "portfolioState":"ACTIVE",
                          "releaseId":"20000000-0000-0000-0000-000000000001",
                          "versionLabel":"1.0.0",
                          "releaseDigest":"abc",
                          "availability":"AVAILABLE"
                        }]
                        """,
                        MediaType.APPLICATION_JSON));

        var result = client().search(
                "Bearer api-token", "onboarding", "CAPABILITY_PACK");

        assertEquals(1, result.size());
        assertEquals(RELEASE_ID, result.getFirst().releaseId());
        server.verify();
    }

    @Test
    void rendersAnExactPromptWithoutInvokingAProvider() {
        server.expect(requestTo(
                        "https://api.example.test/api/asset-delivery/"
                                + ASSET_ID
                                + "/releases/"
                                + RELEASE_ID
                                + "/prompt-render"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION, "Bearer api-token"))
                .andExpect(content().json(
                        "{\"variables\":{\"ticket\":\"Cannot sign in\"}}"))
                .andRespond(withSuccess(
                        """
                        {
                          "assetId":"10000000-0000-0000-0000-000000000001",
                          "releaseId":"20000000-0000-0000-0000-000000000001",
                          "releaseDigest":"abc",
                          "systemInstruction":"Follow policy.",
                          "userPrompt":"Classify Cannot sign in",
                          "sensitiveVariables":["ticket"],
                          "inputShapeDigest":"shape"
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        var result = client().renderPrompt(
                "Bearer api-token",
                ASSET_ID,
                RELEASE_ID,
                Map.of("ticket", "Cannot sign in"));

        assertEquals("Classify Cannot sign in", result.userPrompt());
        assertEquals(List.of("ticket"), result.sensitiveVariables());
        server.verify();
    }

    @Test
    void preservesExplicitNullPromptVariables() {
        server.expect(requestTo(
                        "https://api.example.test/api/asset-delivery/"
                                + ASSET_ID
                                + "/releases/"
                                + RELEASE_ID
                                + "/prompt-render"))
                .andExpect(content().json(
                        "{\"variables\":{\"optional\":null}}"))
                .andRespond(withSuccess(
                        """
                        {
                          "assetId":"10000000-0000-0000-0000-000000000001",
                          "releaseId":"20000000-0000-0000-0000-000000000001",
                          "releaseDigest":"abc",
                          "systemInstruction":"Follow policy.",
                          "userPrompt":"Use the approved default",
                          "sensitiveVariables":[],
                          "inputShapeDigest":"shape"
                        }
                        """,
                        MediaType.APPLICATION_JSON));
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("optional", null);

        var result = client().renderPrompt(
                "Bearer api-token",
                ASSET_ID,
                RELEASE_ID,
                variables);

        assertEquals("Use the approved default", result.userPrompt());
        server.verify();
    }

    @Test
    void mapsDeniedAndMissingAssetsToOneOpaqueMessage() {
        server.expect(requestTo(
                        "https://api.example.test/api/asset-delivery/" + ASSET_ID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("private authorization detail"));
        server.expect(requestTo(
                        "https://api.example.test/api/asset-delivery/" + ASSET_ID))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("denied for actor"));

        var missing = assertThrows(
                AssetDeliveryApiClient.AssetDeliveryGatewayException.class,
                () -> client().getAsset("Bearer api-token", ASSET_ID));
        var denied = assertThrows(
                AssetDeliveryApiClient.AssetDeliveryGatewayException.class,
                () -> client().getAsset("Bearer api-token", ASSET_ID));

        assertFalse(missing.getMessage().contains(
                "private authorization detail"));
        assertFalse(denied.getMessage().contains("denied for actor"));
        assertEquals(missing.getMessage(), denied.getMessage());
        server.verify();
    }

    @Test
    void hidesDownstreamTransportFailureDetails() {
        server.expect(requestTo(
                        "https://api.example.test/api/asset-delivery/" + ASSET_ID))
                .andRespond(withException(
                        new IOException("private network topology")));

        var failure = assertThrows(
                AssetDeliveryApiClient.AssetDeliveryGatewayException.class,
                () -> client().getAsset("Bearer api-token", ASSET_ID));

        assertEquals(
                "OrgMemory Asset delivery is temporarily unavailable",
                failure.getMessage());
        assertNotNull(failure.getCause());
        server.verify();
    }

    private AssetDeliveryApiClient client() {
        return new AssetDeliveryApiClient(builder, properties());
    }

    static McpGatewayProperties properties() {
        return new McpGatewayProperties(
                URI.create("https://api.example.test"),
                Duration.ofSeconds(5),
                URI.create("https://mcp.example.test/mcp"),
                URI.create("https://id.example.test/realms/orgmemory"),
                URI.create("https://id.example.test/realms/orgmemory/protocol/openid-connect/certs"),
                "https://mcp.example.test/mcp",
                "orgmemory-web");
    }
}
