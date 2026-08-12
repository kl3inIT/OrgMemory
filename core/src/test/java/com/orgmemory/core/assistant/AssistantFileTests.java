package com.orgmemory.core.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.storage.ObjectContent;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.error.BusinessNotFoundException;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssistantFileTests {

    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    @Test
    void pinsTheRequestedProfileAcrossRetry() {
        AssistantFile file = file(NOW.plus(Duration.ofDays(30)));
        AssistantFileProcessingProfile first = profile("a");
        AssistantFileProcessingProfile changed = profile("b");

        assertTrue(file.claim("worker", Duration.ofMinutes(5), NOW, first));
        file.fail("worker", "TRANSIENT", true, NOW);

        assertThrows(
                IllegalStateException.class,
                () -> file.claim("worker", Duration.ofMinutes(5), NOW, changed));
    }

    @Test
    void expiryDeniesAClaimBeforeProcessingBegins() {
        AssistantFile file = file(NOW);

        assertFalse(file.claim("worker", Duration.ofMinutes(5), NOW, profile("a")));
        assertEquals(AssistantFileStatus.EXPIRED, file.status());
    }

    @Test
    void deletionIsASeparateDenyStateUntilCleanupCompletes() {
        AssistantFile file = file(NOW.plus(Duration.ofDays(30)));

        file.markDeleting(NOW);
        assertEquals(AssistantFileStatus.DELETING, file.status());
        file.markCleanupComplete(NOW.plusSeconds(1));
        assertEquals(AssistantFileStatus.DELETED, file.status());
        assertTrue(file.cleanupComplete());
    }

    @Test
    void uploadPinsExpiryAndCleansTheObjectWhenRegistrationFails() {
        AssistantFileRepository repository = mock(AssistantFileRepository.class);
        ObjectStoragePort objects = mock(ObjectStoragePort.class);
        CurrentActor actor = actor();
        byte[] content = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        when(objects.put(any(), any())).thenAnswer(invocation -> {
            var request = (com.orgmemory.core.knowledge.storage.ObjectWriteRequest)
                    invocation.getArgument(0);
            return new StoredObject(
                    request.key(), content.length, "application/pdf",
                    "a".repeat(64), "etag", "version");
        });
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AssistantFileService service = new AssistantFileService(
                repository, objects, Clock.fixed(NOW, ZoneOffset.UTC));

        AssistantFileView uploaded = service.upload(
                actor, "C:\\Users\\owner\\policy.pdf", content.length,
                new ByteArrayInputStream(content));

        assertEquals("policy.pdf", uploaded.fileName());
        assertEquals(NOW.plus(Duration.ofDays(30)), uploaded.expiresAt());

        RuntimeException persistenceFailure = new RuntimeException("database unavailable");
        when(repository.saveAndFlush(any())).thenThrow(persistenceFailure);
        assertEquals(
                persistenceFailure,
                assertThrows(
                        RuntimeException.class,
                        () -> service.upload(
                                actor, "policy.pdf", content.length,
                                new ByteArrayInputStream(content))));
        verify(objects).delete(any());
    }

    @Test
    void ownerProbeAndDownloadStayActorScopedAndBrowserSafe() {
        AssistantFileRepository repository = mock(AssistantFileRepository.class);
        ObjectStoragePort objects = mock(ObjectStoragePort.class);
        CurrentActor actor = actor();
        UUID fileId = UUID.randomUUID();
        AssistantFileService service = new AssistantFileService(
                repository, objects, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(BusinessNotFoundException.class, () -> service.get(actor, fileId));
        verify(repository).findByIdAndOrganizationIdAndActorUserId(
                fileId, actor.organizationId(), actor.userId());

        AssistantFile html = new AssistantFile(
                fileId, actor.organizationId(), actor.userId(), "policy.html",
                "text/html", 8, "a".repeat(64),
                "organizations/o/assistant-files/f/policy.html", null, null,
                NOW.plus(Duration.ofDays(30)));
        when(repository.findByIdAndOrganizationIdAndActorUserId(
                        fileId, actor.organizationId(), actor.userId()))
                .thenReturn(java.util.Optional.of(html));
        var stored = new StoredObject(
                new com.orgmemory.core.knowledge.storage.ObjectKey(html.objectKey()),
                8, "text/html", "a".repeat(64), null, null);
        when(objects.open(any())).thenReturn(new ObjectContent(
                new ByteArrayInputStream("<script>".getBytes(StandardCharsets.UTF_8)), stored));

        AssistantFileContent opened = service.open(actor, fileId);

        assertEquals("text/plain", opened.mediaType());
        assertFalse(opened.inlinePreviewAllowed());
    }

    private static AssistantFile file(Instant expiresAt) {
        return new AssistantFile(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "policy.pdf", "application/pdf", 10, "a".repeat(64),
                "organizations/o/assistant-files/f/policy.pdf", null, null, expiresAt);
    }

    private static AssistantFileProcessingProfile profile(String value) {
        return new AssistantFileProcessingProfile("profile=" + value, value.repeat(64));
    }

    private static CurrentActor actor() {
        return new CurrentActor(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Owner", "owner@example.test");
    }
}
