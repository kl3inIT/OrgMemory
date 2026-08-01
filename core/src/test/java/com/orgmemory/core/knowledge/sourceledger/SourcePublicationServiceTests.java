package com.orgmemory.core.knowledge.sourceledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class SourcePublicationServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final UUID REVISION_ID = UUID.randomUUID();

    private final SourceObjectRepository sources = mock(SourceObjectRepository.class);
    private final SourcePublicationService service = new SourcePublicationService(sources);

    @Test
    void joinsTheExistingAssetPublicationTransaction() throws Exception {
        Transactional transaction = SourcePublicationService.class
                .getDeclaredMethod("publishRevision", PublishSourceRevisionCommand.class)
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.MANDATORY, transaction.propagation());
    }

    @Test
    void publishesTheOwnedSourceRevision() {
        SourceObject source = mock(SourceObject.class);
        when(sources.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(source.getOrganizationId()).thenReturn(ORGANIZATION_ID);

        service.publishRevision(new PublishSourceRevisionCommand(
                ORGANIZATION_ID, SOURCE_ID, REVISION_ID));

        verify(source).publishRevision(REVISION_ID);
        verify(sources).save(source);
    }
}
