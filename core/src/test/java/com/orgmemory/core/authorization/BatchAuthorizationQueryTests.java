package com.orgmemory.core.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchAuthorizationQueryTests {

    @Test
    void keepsContextualRelationshipsScopedToTheirRequestedResource() {
        UUID organizationId = UUID.randomUUID();
        ResourceRef requested = ResourceRef.of(organizationId, "knowledge_asset", UUID.randomUUID());
        ResourceRef unrelated = ResourceRef.of(organizationId, "knowledge_asset", UUID.randomUUID());
        ContextualRelationship relationship = ContextualRelationship.of(
                "user:" + UUID.randomUUID(), "owner", requested.openFgaObject());

        var query = new BatchAuthorizationQuery(
                organizationId,
                PrincipalRef.user(UUID.randomUUID()),
                PermissionKey.of("can_view"),
                List.of(requested),
                Map.of(requested, List.of(relationship)));

        assertEquals(List.of(relationship), query.contextualRelationshipsFor(requested));
        assertEquals(List.of(), query.contextualRelationshipsFor(unrelated));
        assertThrows(IllegalArgumentException.class, () -> new BatchAuthorizationQuery(
                organizationId,
                PrincipalRef.user(UUID.randomUUID()),
                PermissionKey.of("can_view"),
                List.of(requested),
                Map.of(unrelated, List.of(relationship))));
    }
}
