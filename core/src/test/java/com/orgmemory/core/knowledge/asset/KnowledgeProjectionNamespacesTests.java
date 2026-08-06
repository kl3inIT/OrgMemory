package com.orgmemory.core.knowledge.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeProjectionNamespacesTests {

    @Test
    void canonicalQueryNamespaceUsesTheOrganizationScopedCanonicalCollection() {
        UUID organizationId =
                UUID.fromString("10000000-0000-0000-0000-000000000001");

        var namespace = KnowledgeProjectionNamespaces.forCanonicalQuery(organizationId);

        assertEquals(organizationId, namespace.organizationId());
        assertEquals("default", namespace.workspace());
        assertEquals("canonical-query", namespace.collection());
    }
}
