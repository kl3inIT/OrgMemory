package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class SkillPublicationControllerTests {

    private static final UUID SPACE_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    private final SkillPublicationApiClient publications =
            mock(SkillPublicationApiClient.class);
    private final McpApiAuthorization authorization =
            mock(McpApiAuthorization.class);
    private final SkillPublicationController controller =
            new SkillPublicationController(publications, authorization);

    @Test
    void exchangesTheWriteScopedIdentityAndForwardsOneDraftRequest() {
        var authentication = mock(Authentication.class);
        var file = new MockMultipartFile(
                "file",
                "expense-review.zip",
                "application/zip",
                "package".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var expected = new SkillPublicationApiClient.SkillDraftPublication(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "SKILL",
                "finance",
                "expense-review",
                SPACE_ID,
                new SkillPublicationApiClient.SkillDraftPublication.Draft(
                        UUID.fromString(
                                "20000000-0000-0000-0000-000000000001"),
                        0,
                        "Expense review",
                        "Review an expense"));
        when(authorization.requirePublisher(authentication))
                .thenReturn("Bearer publisher-token");
        when(publications.createDraft(
                        "Bearer publisher-token",
                        file,
                        "finance",
                        SPACE_ID,
                        "INTERNAL"))
                .thenReturn(expected);

        var result = controller.createDraft(
                file,
                "finance",
                SPACE_ID,
                "internal",
                authentication);

        assertEquals(expected, result);
        verify(authorization).requirePublisher(authentication);
    }

    @Test
    void rejectsAnEmptyPackageBeforeTokenExchange() {
        var failure = assertThrows(
                ResponseStatusException.class,
                () -> controller.createDraft(
                        new MockMultipartFile(
                                "file",
                                "empty.zip",
                                "application/zip",
                                new byte[0]),
                        "finance",
                        SPACE_ID,
                        "INTERNAL",
                        mock(Authentication.class)));

        assertEquals(413, failure.getStatusCode().value());
    }

    @Test
    void rejectsAnUnknownClassification() {
        var failure = assertThrows(
                ResponseStatusException.class,
                () -> controller.createDraft(
                        new MockMultipartFile(
                                "file",
                                "skill.zip",
                                "application/zip",
                                new byte[] {1}),
                        "finance",
                        SPACE_ID,
                        "SECRET",
                        mock(Authentication.class)));

        assertEquals(400, failure.getStatusCode().value());
    }

    @Test
    void mapsDownstreamAuthorizationFailureToAnOpaqueUnavailableResponse() {
        var authentication = mock(Authentication.class);
        when(authorization.requirePublisher(authentication))
                .thenThrow(new McpGatewayException(
                        "private issuer response"));

        var failure = assertThrows(
                ResponseStatusException.class,
                () -> controller.createDraft(
                        new MockMultipartFile(
                                "file",
                                "skill.zip",
                                "application/zip",
                                new byte[] {1}),
                        "finance",
                        SPACE_ID,
                        "INTERNAL",
                        authentication));

        assertEquals(503, failure.getStatusCode().value());
        assertEquals(
                "OrgMemory could not authorize Skill publication",
                failure.getReason());
    }
}
