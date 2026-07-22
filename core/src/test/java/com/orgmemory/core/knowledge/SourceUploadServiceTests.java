package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.permission.KnowledgePermissionPolicy;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class SourceUploadServiceTests {

    @Test
    void checksParentSpacePermissionBeforeWritingEvidence() {
        UUID organizationId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(), organizationId, departmentId, "Uploader", "uploader@example.com");
        ObjectStoragePort objects = mock(ObjectStoragePort.class);
        SourceUploadRegistrationService registrations = mock(SourceUploadRegistrationService.class);
        KnowledgeSpaceService spaces = mock(KnowledgeSpaceService.class);
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
                "text/plain",
                4,
                KnowledgeClassification.CONFIDENTIAL,
                spaceId);

        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> service.upload(command, new ByteArrayInputStream(new byte[] {1, 2, 3, 4})));
        verifyNoInteractions(objects, registrations);
    }
}
