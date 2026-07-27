package com.orgmemory.core.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.orgmemory.core.shared.error.BusinessValidationException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoleAdministrationServiceTests {

    private static final UUID MINH_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID ORGANIZATION_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
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
    void assigningARoleWritesOneTenantScopedOrganizationTuple() {
        when(writes.write(any())).thenReturn(RelationshipTupleWriteResult.applied(POLICY));

        service.assign(ORGANIZATION_ID, "organization-admin", MINH_ID);

        var captor = ArgumentCaptor.forClass(RelationshipTupleWriteRequest.class);
        verify(writes).write(captor.capture());
        assertEquals(
                List.of(RelationshipTuple.of(
                        "user:" + MINH_ID,
                        "administrator",
                        "organization:" + ORGANIZATION_ID)),
                captor.getValue().tuples());
    }

    @Test
    void anAdministratorCannotWriteAgainstContentTheSourceSystemOwns() {
        var assetTuple = RelationshipTuple.of("user:" + MINH_ID, "viewer", "knowledge_asset:" + ASSET_ID);

        var refusal = assertThrows(
                IllegalArgumentException.class, () -> AdministrativeTupleScope.require(assetTuple));

        assertTrue(refusal.getMessage().contains("knowledge_asset"));
        assertFalse(AdministrativeTupleScope.writable("knowledge_asset"));
        assertTrue(AdministrativeTupleScope.writable("knowledge_space"));
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
                BusinessValidationException.class,
                () -> service.assign(
                        ORGANIZATION_ID,
                        "organization-admin:knowledge_asset",
                        MINH_ID));
        assertThrows(
                BusinessValidationException.class,
                () -> service.assign(ORGANIZATION_ID, "  ", MINH_ID));
    }

    @Test
    void listingGroupsAssigneesByRoleAndIgnoresEverythingElse() {
        when(tuples.read(any(RelationshipTupleFilter.class), anyInt(), any()))
                .thenReturn(RelationshipTuplePage.resolved(
                List.of(
                        RelationshipTuple.of(
                                "user:" + MINH_ID,
                                "administrator",
                                "organization:" + ORGANIZATION_ID),
                        RelationshipTuple.of(
                                "user:someone",
                                "administrator",
                                "organization:" + ORGANIZATION_ID),
                        RelationshipTuple.of(
                                "user:someone",
                                "member",
                                "organization:" + ORGANIZATION_ID)),
                null,
                POLICY));

        var listing = service.roles(ORGANIZATION_ID);

        var admin = listing.roles().stream()
                .filter(role -> role.role().equals("organization-admin"))
                .findFirst()
                .orElseThrow();
        assertEquals(2, admin.assignees().size());
        assertTrue(listing.complete());
        verify(tuples)
                .read(
                        eq(RelationshipTupleFilter.object(
                                "organization:" + ORGANIZATION_ID)),
                        anyInt(),
                        any());
    }

    /**
     * The tuple cap does not bound the loop on its own: a page carrying a continuation token but no
     * tuples advances neither the count nor the cursor, so the listing would spin on it and take an
     * administrative endpoint with it.
     */
    @Test
    void aStoreThatKeepsHandingBackAnEmptyPageStillTerminates() {
        when(tuples.read(any(RelationshipTupleFilter.class), anyInt(), any()))
                .thenReturn(RelationshipTuplePage.resolved(List.of(), "same-token-forever", POLICY));

        var listing = assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> service.roles(ORGANIZATION_ID),
                "the role listing must terminate");

        assertFalse(listing.complete());
        assertTrue(listing.roles().stream().allMatch(role -> role.assignees().isEmpty()));
    }

    @Test
    void anUnreadableStoreReportsIncompleteRatherThanEmpty() {
        when(tuples.read(any(RelationshipTupleFilter.class), anyInt(), any()))
                .thenReturn(RelationshipTuplePage.indeterminate("OPENFGA_TIMEOUT", POLICY));

        var listing = service.roles(ORGANIZATION_ID);

        assertTrue(listing.roles().isEmpty());
        assertFalse(listing.complete());
    }

    @Test
    void aDifferentOrganizationCannotAppearInTheListing() {
        UUID foreignOrganization =
                UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(tuples.read(any(RelationshipTupleFilter.class), anyInt(), any()))
                .thenReturn(RelationshipTuplePage.resolved(
                        List.of(RelationshipTuple.of(
                                "user:foreign",
                                "administrator",
                                "organization:" + foreignOrganization)),
                        null,
                        POLICY));

        var listing = service.roles(ORGANIZATION_ID);

        assertTrue(listing.roles().stream()
                .allMatch(role -> role.assignees().isEmpty()));
    }
}
