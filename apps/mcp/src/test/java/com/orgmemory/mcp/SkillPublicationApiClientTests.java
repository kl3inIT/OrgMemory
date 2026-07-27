package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class SkillPublicationApiClientTests {

    private static final UUID SPACE_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void forwardsThePackageOnlyToTheCanonicalSkillImport() {
        server.expect(requestTo(
                        "https://api.example.test/api/assets/skills"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer publisher-token"))
                .andRespond(withSuccess(
                        """
                        {
                          "id":"10000000-0000-0000-0000-000000000001",
                          "type":"SKILL",
                          "namespace":"finance",
                          "slug":"expense-review",
                          "knowledgeSpaceId":"30000000-0000-0000-0000-000000000001",
                          "draft":{
                            "id":"20000000-0000-0000-0000-000000000001",
                            "lockVersion":0,
                            "title":"Expense review",
                            "summary":"Review an expense"
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        var result = client().createDraft(
                "Bearer publisher-token",
                packageFile(),
                "finance",
                SPACE_ID,
                "INTERNAL");

        assertEquals("expense-review", result.slug());
        assertEquals(0, result.draft().lockVersion());
        server.verify();
    }

    @Test
    void mapsDuplicateIdentityWithoutLeakingDownstreamDetails() {
        server.expect(requestTo(
                        "https://api.example.test/api/assets/skills"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("private database constraint"));

        var failure = assertThrows(
                ResponseStatusException.class,
                () -> client().createDraft(
                        "Bearer publisher-token",
                        packageFile(),
                        "finance",
                        SPACE_ID,
                        "INTERNAL"));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        assertEquals(
                "A Skill already uses this namespace and name",
                failure.getReason());
        server.verify();
    }

    private SkillPublicationApiClient client() {
        return new SkillPublicationApiClient(
                builder, AssetDeliveryApiClientTests.properties());
    }

    private static MockMultipartFile packageFile() {
        return new MockMultipartFile(
                "file",
                "expense-review.zip",
                "application/zip",
                "package".getBytes(StandardCharsets.UTF_8));
    }
}
