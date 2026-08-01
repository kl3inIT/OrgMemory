package com.orgmemory.core.knowledge.acl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceAclFacadeTests {

    @Test
    void rejectsInvalidWritesBeforeTouchingPersistence() {
        SourceAclSnapshotRepository snapshots = mock(SourceAclSnapshotRepository.class);
        SourceAclEntryRepository entries = mock(SourceAclEntryRepository.class);
        SourceAclSnapshotSealRepository seals = mock(SourceAclSnapshotSealRepository.class);
        SourceAclHeadRepository heads = mock(SourceAclHeadRepository.class);
        SourceAclFacade facade = new SourceAclFacade(snapshots, entries, seals, heads);
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        SourceAclTarget target = new SourceAclTarget(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "google-drive",
                "connection-1",
                "document-1");

        assertThrows(
                BusinessValidationException.class,
                () -> facade.createAndAdvance(
                        target,
                        null,
                        AclCaptureStatus.COMPLETE,
                        AccessGate.DENY,
                        now.minusSeconds(1),
                        List.of(),
                        false,
                        now));

        verifyNoInteractions(snapshots, entries, seals, heads);
    }
}
