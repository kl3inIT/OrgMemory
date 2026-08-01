package com.orgmemory.core.knowledge.space;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeSpaceQueryTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();

    private final KnowledgeSpaceRepository spaces = mock(KnowledgeSpaceRepository.class);
    private final KnowledgeSpaceQuery query = new KnowledgeSpaceQuery(spaces);

    @Test
    void distinguishesTenantExistenceFromActiveAvailability() {
        when(spaces.existsByIdAndOrganizationId(SPACE_ID, ORGANIZATION_ID))
                .thenReturn(true);
        when(spaces.existsByIdAndOrganizationIdAndActiveTrue(
                        SPACE_ID, ORGANIZATION_ID))
                .thenReturn(false);

        assertTrue(query.exists(ORGANIZATION_ID, SPACE_ID));
        assertFalse(query.isActive(ORGANIZATION_ID, SPACE_ID));
    }
}
