package com.orgmemory.core.knowledge.sourceledger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceLifecycleServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final String SOURCE_SYSTEM = "slack";
    private static final String CONNECTION_KEY = "workspace-1";
    private static final String EXTERNAL_OBJECT_ID = "channel-1";

    private final SourceObjectRepository sources = mock(SourceObjectRepository.class);
    private final SourceLifecycleService lifecycle = new SourceLifecycleService(sources);

    @Test
    void retiresAnActiveSource() {
        SourceObject source = mock(SourceObject.class);
        when(sources.findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        ORGANIZATION_ID,
                        SOURCE_SYSTEM,
                        CONNECTION_KEY,
                        EXTERNAL_OBJECT_ID))
                .thenReturn(Optional.of(source));
        when(source.getStatus()).thenReturn(SourceObjectStatus.ACTIVE);

        assertTrue(lifecycle.retire(
                ORGANIZATION_ID, SOURCE_SYSTEM, CONNECTION_KEY, EXTERNAL_OBJECT_ID));

        verify(source).archive();
        verify(sources).saveAndFlush(source);
    }

    @Test
    void missingOrAlreadyArchivedSourcesAreUnchanged() {
        SourceObject archived = mock(SourceObject.class);
        when(archived.getStatus()).thenReturn(SourceObjectStatus.ARCHIVED);
        when(sources.findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        ORGANIZATION_ID,
                        SOURCE_SYSTEM,
                        CONNECTION_KEY,
                        "archived"))
                .thenReturn(Optional.of(archived));

        assertFalse(lifecycle.retire(
                ORGANIZATION_ID, SOURCE_SYSTEM, CONNECTION_KEY, "missing"));
        assertFalse(lifecycle.retire(
                ORGANIZATION_ID, SOURCE_SYSTEM, CONNECTION_KEY, "archived"));

        verify(archived, never()).archive();
        verify(sources, never()).saveAndFlush(archived);
    }
}
