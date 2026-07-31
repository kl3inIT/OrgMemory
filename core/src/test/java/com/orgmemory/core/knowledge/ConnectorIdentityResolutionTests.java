package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.acl.SourcePrincipalKind;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orgmemory.core.knowledge.ConnectorIdentityResolution.PrincipalKey;
import com.orgmemory.core.knowledge.ConnectorIdentityResolution.ResolvedPrincipal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectorIdentityResolutionTests {

    @Test
    void principalKindIsPartOfTheNativeIdentityKey() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        ConnectorIdentityResolution resolution = new ConnectorIdentityResolution(Map.of(
                new PrincipalKey(SourcePrincipalKind.SOURCE_USER, "shared-id"),
                new ResolvedPrincipal(userId, SourcePrincipalKind.SOURCE_USER),
                new PrincipalKey(SourcePrincipalKind.SOURCE_GROUP, "shared-id"),
                new ResolvedPrincipal(groupId, SourcePrincipalKind.SOURCE_GROUP)));

        assertEquals(
                userId,
                resolution.find(SourcePrincipalKind.SOURCE_USER, "shared-id").id());
        assertEquals(
                groupId,
                resolution.find(SourcePrincipalKind.SOURCE_GROUP, "shared-id").id());
    }
}
