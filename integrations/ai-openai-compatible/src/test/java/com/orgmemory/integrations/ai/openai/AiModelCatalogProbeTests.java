package com.orgmemory.integrations.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.ai.AiGatewayEndpointPolicy;
import com.orgmemory.core.ai.AiGatewayPreset;
import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.core.shared.secret.SecretValue;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AiModelCatalogProbeTests {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void discoversAndSortsABoundedOpenAiCompatibleCatalog()
            throws IOException {
        byte[] response = """
                {"data":[
                  {"id":"model-z","display_name":"Zulu"},
                  {"id":"model-a","display_name":"Alpha"}
                ]}
                """.getBytes(StandardCharsets.UTF_8);
        String baseUrl = serve(200, response);
        AiModelCatalogProbe probe = probe(baseUrl);

        AiModelCatalogProbe.Result result = probe.transientProbe(
                AiGatewayPreset.LITELLM,
                AiGatewayProtocol.OPENAI_COMPATIBLE,
                baseUrl,
                SecretValue.of("test-key"),
                Duration.ofSeconds(2));

        assertTrue(result.authenticated());
        assertEquals(
                java.util.List.of("model-a", "model-z"),
                result.models().stream()
                        .map(AiModelCatalogProbe.ModelRef::id)
                        .toList());
    }

    @Test
    void rejectsAnOversizedCatalogBeforeJsonParsing()
            throws IOException {
        byte[] response = new byte[1_048_577];
        java.util.Arrays.fill(response, (byte) 'x');
        String baseUrl = serve(200, response);
        AiModelCatalogProbe probe = probe(baseUrl);

        AiModelCatalogProbe.Result result = probe.transientProbe(
                AiGatewayPreset.LITELLM,
                AiGatewayProtocol.OPENAI_COMPATIBLE,
                baseUrl,
                SecretValue.of("test-key"),
                Duration.ofSeconds(2));

        assertFalse(result.authenticated());
        assertEquals("response_too_large", result.errorCode());
    }

    @Test
    void mapsAuthenticationFailureWithoutReturningProviderBody()
            throws IOException {
        String baseUrl = serve(
                401,
                "provider secret details".getBytes(StandardCharsets.UTF_8));
        AiModelCatalogProbe probe = probe(baseUrl);

        AiModelCatalogProbe.Result result = probe.transientProbe(
                AiGatewayPreset.LITELLM,
                AiGatewayProtocol.OPENAI_COMPATIBLE,
                baseUrl,
                SecretValue.of("invalid-key"),
                Duration.ofSeconds(2));

        assertFalse(result.authenticated());
        assertEquals("invalid_credential", result.errorCode());
        assertFalse(result.toString().contains("provider secret details"));
        assertFalse(result.toString().contains("invalid-key"));
    }

    private AiModelCatalogProbe probe(String baseUrl) {
        AiGatewayEndpointPolicy endpoints =
                mock(AiGatewayEndpointPolicy.class);
        when(endpoints.requireAllowed(
                        AiGatewayPreset.LITELLM,
                        AiGatewayProtocol.OPENAI_COMPATIBLE,
                        baseUrl))
                .thenReturn(baseUrl);
        return new AiModelCatalogProbe(
                endpoints,
                new ObjectMapper());
    }

    private String serve(int status, byte[] response)
            throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(
                        InetAddress.getLoopbackAddress(),
                        0),
                0);
        server.createContext("/v1/models", exchange -> {
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        return "http://localhost:"
                + server.getAddress().getPort()
                + "/v1";
    }
}
