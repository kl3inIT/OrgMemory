package com.orgmemory.core.knowledge.sourceledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.ObjectWriteRequest;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.permission.KnowledgePermissionPolicy;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;

class SourceUploadServiceTests {

    @Test
    void derivesTheStoredMediaTypeFromTheAllowlistedExtension() {
        UUID organizationId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(), organizationId, departmentId, "Uploader", "uploader@example.com");
        ObjectStoragePort objects = mock(ObjectStoragePort.class);
        SourceUploadRegistrationService registrations =
                mock(SourceUploadRegistrationService.class);
        SourceKnowledgeSpacePort spaces = mock(SourceKnowledgeSpacePort.class);
        when(spaces.requireUploadTarget(actor, spaceId)).thenReturn(
                new SourceKnowledgeSpaceRef(
                        spaceId, "people", "People", departmentId));
        when(objects.put(any(), any())).thenReturn(new StoredObject(
                new ObjectKey("organizations/test/sources/workflow.txt"),
                4,
                "text/plain",
                "sha256",
                "etag",
                "version"));
        when(registrations.register(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(mock(SourceUploadResult.class));
        SourceUploadService service = new SourceUploadService(
                objects,
                registrations,
                new KnowledgePermissionPolicy(),
                new SourceIngestionProperties(DataSize.ofMegabytes(25), 5),
                spaces);

        service.upload(
                new CreateUploadSourceCommand(
                        actor,
                        "workflow.txt",
                        4,
                        KnowledgeClassification.CONFIDENTIAL,
                        spaceId),
                new ByteArrayInputStream(
                        new byte[] {1, 2, 3, 4}));

        ArgumentCaptor<ObjectWriteRequest> request =
                ArgumentCaptor.forClass(ObjectWriteRequest.class);
        verify(objects).put(request.capture(), any());
        assertEquals(
                "text/plain",
                request.getValue().mediaType());
    }

    @Test
    void checksParentSpacePermissionBeforeWritingEvidence() {
        UUID organizationId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(), organizationId, departmentId, "Uploader", "uploader@example.com");
        ObjectStoragePort objects = mock(ObjectStoragePort.class);
        SourceUploadRegistrationService registrations = mock(SourceUploadRegistrationService.class);
        SourceKnowledgeSpacePort spaces = mock(SourceKnowledgeSpacePort.class);
        when(spaces.requireUploadTarget(actor, spaceId)).thenThrow(
                new OrgMemoryAccessDeniedException("Not authorized"));
        SourceUploadService service = new SourceUploadService(
                objects,
                registrations,
                new KnowledgePermissionPolicy(),
                new SourceIngestionProperties(DataSize.ofMegabytes(25), 5),
                spaces);

        CreateUploadSourceCommand command = new CreateUploadSourceCommand(
                actor,
                "workflow.txt",
                4,
                KnowledgeClassification.CONFIDENTIAL,
                spaceId);

        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> service.upload(command, new ByteArrayInputStream(new byte[] {1, 2, 3, 4})));
        verifyNoInteractions(objects, registrations);
    }

    @Test
    void rejectsAFileSystemRootAsAnInvalidUploadFileName() {
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Uploader",
                "uploader@example.com");
        ObjectStoragePort objects = mock(ObjectStoragePort.class);
        SourceUploadRegistrationService registrations =
                mock(SourceUploadRegistrationService.class);
        SourceUploadService service = new SourceUploadService(
                objects,
                registrations,
                new KnowledgePermissionPolicy(),
                new SourceIngestionProperties(DataSize.ofMegabytes(25), 5),
                mock(SourceKnowledgeSpacePort.class));

        BusinessValidationException failure = assertThrows(
                BusinessValidationException.class,
                () -> service.upload(
                        new CreateUploadSourceCommand(
                                actor,
                                File.separator,
                                4,
                                KnowledgeClassification.INTERNAL,
                                UUID.randomUUID()),
                        new ByteArrayInputStream(
                                new byte[] {1, 2, 3, 4})));

        assertEquals("source.upload-filename-invalid", failure.code());
        verifyNoInteractions(objects, registrations);
    }

    @Test
    void rejectsASpreadsheetAboveItsFormatLimitWithAnActionableMessage() {
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Uploader",
                "uploader@example.com");
        ObjectStoragePort objects = mock(ObjectStoragePort.class);
        SourceUploadRegistrationService registrations = mock(SourceUploadRegistrationService.class);
        SourceUploadService service = new SourceUploadService(
                objects,
                registrations,
                new KnowledgePermissionPolicy(),
                new SourceIngestionProperties(DataSize.ofMegabytes(25), 5),
                mock(SourceKnowledgeSpacePort.class));

        BusinessValidationException failure = assertThrows(
                BusinessValidationException.class,
                () -> service.upload(
                        new CreateUploadSourceCommand(
                                actor,
                                "headcount.xlsx",
                                DataSize.ofMegabytes(16).toBytes(),
                                KnowledgeClassification.INTERNAL,
                                UUID.randomUUID()),
                        new ByteArrayInputStream(new byte[] {1})));

        assertEquals("source.upload-size-invalid", failure.code());
        assertEquals("file exceeds the 15 MB limit for .xlsx documents", failure.getMessage());
        verifyNoInteractions(objects, registrations);
    }
}
