package com.orgmemory.core.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoleAdministrationServiceTests {

    private static final UUID MINH_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID ASSET_ID = UUID.fromString("25000000-0000-4000-8000-000000000006");
    private static final UUID SPACE_ID = UUID.fromString("88888888-8888-4888-8888-888888888801");
    private static final String POLICY = "01KY4JS83SQ11R9RMKAMYCPH2Q";

    private RelationshipTupleWritePort writes;
    private RelationshipTupleReconciliationPort tuples;
    private RoleAdministrationService service;

    @BeforeEach
    void setUp() {
        writes = mock(RelationshipTupleWritePort.class);
        tuples = mock(RelationshipTupleReconciliationPort.class);
        when(tuples.policyVersion()).thenReturn(POLICY);
        service = new RoleAdministrationService(writes, tuples);
    }

    @Test
    void assigningARoleWritesOneAssigneeTuple() {
        when(writes.write(any())).thenReturn(RelationshipTupleWriteResult.applied(POLICY));

        service.assign("organization-admin", MINH_ID);

        var captor = ArgumentCaptor.forClass(RelationshipTupleWriteRequest.class);
        verify(writes).write(captor.capture());
        assertEquals(
                List.of(RelationshipTuple.of(
                        "user:" + MINH_ID, "assignee", "role:organization-admin")),
                captor.getValue().tuples());
    }

    @Test
    void anAdministratorCannotWriteAgainstContentTheSourceSystemOwns() {
        var assetTuple = RelationshipTuple.of("user:" + MINH_ID, "viewer", "knowledge_asset:" + ASSET_ID);

        var refusal = assertThrows(
                IllegalArgumentException.class, () -> AdministrativeTupleScope.require(assetTuple));

        assertTrue(refusal.getMessage().contains("knowledge_asset"));
        assertFalse(AdministrativeTupleScope.writable("knowledge_asset"));
        assertTrue(AdministrativeTupleScope.writable("role"));
        assertTrue(AdministrativeTupleScope.writable("organization"));
    }

    /**
     * A Knowledge Space has no source counterpart to diverge from — {@code acl_authority} is a
     * column on {@code source_objects}, not on {@code knowledge_spaces} — so OrgMemory is its only
     * writer and an administrator may author its grants.
     */
    @Test
    void anAdministratorMayWriteAgainstAKnowledgeSpaceOrgMemoryOwns() {
        var spaceTuple = RelationshipTuple.of("user:" + MINH_ID, "viewer", "knowledge_space:" + SPACE_ID);

        assertEquals(spaceTuple, AdministrativeTupleScope.require(spaceTuple));
        assertTrue(AdministrativeTupleScope.writable("knowledge_space"));
    }

    @Test
    void aRoleNameCannotSmuggleAnotherObjectReference() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.assign("organization-admin:knowledge_asset", MINH_ID));
        assertThrows(IllegalArgumentException.class, () -> service.assign("  ", MINH_ID));
    }

    @Test
    void listingGroupsAssigneesByRoleAndIgnoresEverythingElse() {
        when(tuples.read(anyInt(), any())).thenReturn(RelationshipTuplePage.resolved(
                List.of(
                        RelationshipTuple.of("user:" + MINH_ID, "assignee", "role:organization-admin"),
                        RelationshipTuple.of("user:someone", "assignee", "role:organization-admin"),
                        RelationshipTuple.of("user:someone", "member", "organization:acme")),
                null,
                POLICY));

        var listing = service.roles();

        assertEquals(1, listing.roles().size());
        assertEquals("organization-admin", listing.roles().getFirst().role());
        assertEquals(2, listing.roles().getFirst().assignees().size());
        assertTrue(listing.complete());
    }

    @Test
    void anUnreadableStoreReportsIncompleteRatherThanEmpty() {
        when(tuples.read(anyInt(), any()))
                .thenReturn(RelationshipTuplePage.indeterminate("OPENFGA_TIMEOUT", POLICY));

        var listing = service.roles();

        assertTrue(listing.roles().isEmpty());
        assertFalse(listing.complete());
    }
}
