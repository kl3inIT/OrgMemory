package com.orgmemory.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.orgmemory.core.assetregistry.AssetConflictException;
import com.orgmemory.core.assetregistry.AssetNotFoundException;
import com.orgmemory.core.assetregistry.AssetUnavailableException;
import com.orgmemory.core.assistant.AssistantConversationNotFoundException;
import com.orgmemory.core.knowledge.CitationNotFoundException;
import com.orgmemory.core.knowledge.UnsupportedConnectorSourceException;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiExceptionHandlerTests {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void missingAndUnauthorizedResourcesShareOneOpaqueContract() {
        var citation = handler.business(new CitationNotFoundException());
        var asset = handler.business(new AssetNotFoundException());

        assertEquals(HttpStatus.NOT_FOUND.value(), citation.getStatus());
        assertEquals(citation.getDetail(), asset.getDetail());
        assertEquals(citation.getType(), asset.getType());
        assertEquals(URI.create("/api/resources"), citation.getInstance());
        assertEquals(citation.getInstance(), asset.getInstance());
        assertEquals(
                "knowledge.resource-not-available",
                citation.getProperties().get("code"));
    }

    @Test
    void validationAndForbiddenAreMappedWithoutConcreteHandlerMethods() {
        var validation = handler.business(
                new UnsupportedConnectorSourceException(
                        "Connector source is unsupported"));
        var forbidden = handler.business(
                new OrgMemoryAccessDeniedException("Access denied"));

        assertEquals(HttpStatus.BAD_REQUEST.value(), validation.getStatus());
        assertEquals(
                "connector.source-unsupported",
                validation.getProperties().get("code"));
        assertEquals(HttpStatus.FORBIDDEN.value(), forbidden.getStatus());
        assertEquals(
                "access.denied",
                forbidden.getProperties().get("code"));
    }

    @Test
    void conversationNotFoundCarriesStableMachineCode() {
        UUID conversationId = UUID.randomUUID();
        var response = handler.business(
                new AssistantConversationNotFoundException(conversationId));

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus());
        assertEquals(
                "Assistant conversation not found: " + conversationId,
                response.getDetail());
        assertEquals(
                URI.create(
                        "urn:orgmemory:problem:assistant.conversation-not-found"),
                response.getType());
    }

    @Test
    void conflictAndUnavailableAreMappedByBusinessCategory() {
        var conflict =
                handler.business(new AssetConflictException("changed"));
        var unavailable =
                handler.business(new AssetUnavailableException("retry later"));

        assertEquals(HttpStatus.CONFLICT.value(), conflict.getStatus());
        assertEquals("asset.conflict", conflict.getProperties().get("code"));
        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                unavailable.getStatus());
        assertEquals(
                "asset.unavailable",
                unavailable.getProperties().get("code"));
    }

    @Test
    void publicProblemNeverContainsInternalCause() {
        var response = handler.business(new AssetConflictException(
                "The operation conflicted",
                new IllegalStateException("database row and internal key")));

        assertEquals("The operation conflicted", response.getDetail());
        assertNull(response.getProperties().get("cause"));
    }

    @Test
    void legacyInvalidArgumentKeepsStatusButHidesItsMessage() {
        var response = handler.legacyInvalidArgument(
                new IllegalArgumentException(
                        "private invariant and database identity"));

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
        assertEquals("The request is invalid", response.getDetail());
        assertEquals(
                "request.invalid-argument",
                response.getProperties().get("code"));
    }
}
