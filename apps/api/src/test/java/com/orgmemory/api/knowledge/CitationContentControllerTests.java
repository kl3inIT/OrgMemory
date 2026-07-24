package com.orgmemory.api.knowledge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.CitationContent;
import com.orgmemory.core.knowledge.CitationContentService;
import com.orgmemory.core.knowledge.storage.ObjectContent;
import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;

class CitationContentControllerTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("44000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("44000000-0000-0000-0000-000000000002");
    private static final UUID CHUNK_ID =
            UUID.fromString("44000000-0000-0000-0000-000000000003");
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID,
            ORGANIZATION_ID,
            null,
            "User",
            "user@example.test");

    @Test
    void streamsVerifiedEvidenceThroughTheBackendWithoutExposingStorageKeys()
            throws IOException {
        byte[] payload = "approved evidence".getBytes(StandardCharsets.UTF_8);
        CloseTrackingInputStream stream =
                new CloseTrackingInputStream(payload);
        CitationContent citation = citation(
                stream,
                payload.length,
                MediaType.TEXT_PLAIN_VALUE);
        CitationContentService citations =
                mock(CitationContentService.class);
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        Authentication authentication = mock(Authentication.class);
        when(actors.current(authentication)).thenReturn(ACTOR);
        when(citations.open(
                        org.mockito.ArgumentMatchers.eq(ACTOR),
                        org.mockito.ArgumentMatchers.eq(CHUNK_ID),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(citation);

        var response =
                new CitationContentController(citations, actors)
                        .content(CHUNK_ID, authentication);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
        assertEquals(payload.length, response.getHeaders().getContentLength());
        assertEquals(
                CacheControl.noStore().getHeaderValue(),
                response.getHeaders().getCacheControl());
        assertEquals(
                "nosniff",
                response.getHeaders().getFirst("X-Content-Type-Options"));
        assertTrue(response.getHeaders()
                .getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .startsWith("inline;"));
        assertFalse(response.getHeaders().toString()
                .contains("private/evidence/object-key"));
        assertArrayEquals(payload, output.toByteArray());
        assertTrue(stream.closed);
        verify(actors).current(authentication);
    }

    @Test
    void fallsBackToBinaryForAnUntrustedStoredMediaType() {
        CitationContent citation = citation(
                new CloseTrackingInputStream(new byte[0]),
                0,
                "not a media type");
        CitationContentService citations =
                mock(CitationContentService.class);
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        Authentication authentication = mock(Authentication.class);
        when(actors.current(authentication)).thenReturn(ACTOR);
        when(citations.open(
                        org.mockito.ArgumentMatchers.eq(ACTOR),
                        org.mockito.ArgumentMatchers.eq(CHUNK_ID),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(citation);

        var response =
                new CitationContentController(citations, actors)
                        .content(CHUNK_ID, authentication);

        assertEquals(
                MediaType.APPLICATION_OCTET_STREAM,
                response.getHeaders().getContentType());
    }

    private static CitationContent citation(
            CloseTrackingInputStream stream,
            long contentLength,
            String mediaType) {
        ObjectKey key = new ObjectKey("private/evidence/object-key");
        return new CitationContent(
                CHUNK_ID,
                "leave-policy.txt",
                mediaType,
                contentLength,
                "sha256",
                new ObjectContent(
                        stream,
                        new StoredObject(
                                key,
                                contentLength,
                                mediaType,
                                "sha256",
                                "etag",
                                "version")));
    }

    private static final class CloseTrackingInputStream
            extends ByteArrayInputStream {

        private boolean closed;

        private CloseTrackingInputStream(byte[] payload) {
            super(payload);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
