package com.orgmemory.core.knowledge.space;

import com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipTuple;
import com.orgmemory.core.authorization.RelationshipTuplePage;
import com.orgmemory.core.authorization.RelationshipTupleReconciliationPort;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RelationshipTupleWriteRequest;
import com.orgmemory.core.authorization.RelationshipTupleWriteResult;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.DepartmentRepository;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceSubject.Kind;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

class KnowledgeSpaceAdministrationServiceTests {

    private static final String POLICY = "01KY4JS83SQ11R9RMKAMYCPH2Q";
    private static final UUID ORG = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEPT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN = UUID.fromString("13000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_ORG_USER = UUID.fromString("13000000-0000-4000-8000-0000000000ff");
    private static final CurrentActor ACTOR =
            new CurrentActor(ADMIN, ORG, null, "Quan", "quan@example.test");

    private KnowledgeSpaceRepository spaces;
    private DepartmentRepository departments;
    private AppUserRepository users;
    private KnowledgeSpaceCustomViewerGrantRepository customViewers;
    private RelationshipAuthorizationPort authorization;
    private RelationshipTupleWritePort writes;
    private RelationshipTupleReconciliationPort tuples;
    private KnowledgeSpaceAdministrationService service;

    @BeforeEach
    void setUp() {
        spaces = mock(KnowledgeSpaceRepository.class);
        departments = mock(DepartmentRepository.class);
        users = mock(AppUserRepository.class);
        customViewers = mock(KnowledgeSpaceCustomViewerGrantRepository.class);
        authorization = mock(RelationshipAuthorizationPort.class);
        writes = mock(RelationshipTupleWritePort.class);
        tuples = mock(RelationshipTupleReconciliationPort.class);
        service = new KnowledgeSpaceAdministrationService(
                spaces,
                departments,
                users,
                customViewers,
                authorization,
                writes,
                tuples,
                mock(PermissionAuditService.class));

        when(authorization.check(any())).thenReturn(AuthorizationDecision.allow(POLICY));
        when(tuples.policyVersion()).thenReturn(POLICY);
        when(tuples.readObject(anyString(), anyInt(), any()))
                .thenReturn(RelationshipTuplePage.resolved(List.of(), null, POLICY));
        when(spaces.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(writes.write(any())).thenReturn(RelationshipTupleWriteResult.applied(POLICY));
    }

    /**
     * The {@code organization} tuple is the one worth asserting: {@code org_admin} resolves through
     * {@code administrator from organization}, so a space created without it is unreachable even to
     * an organization administrator.
     */
    @Test
    void creatingASpaceWritesTheParentLinkAndMakesTheCreatorItsAdministrator() {
        var created = service.create(
                ACTOR,
                "Sales Knowledge",
                KnowledgeSpaceAudienceMode.ORGANIZATION,
                null,
                "request-1");

        assertEquals("sales-knowledge", created.key());
        var captor = ArgumentCaptor.forClass(RelationshipTupleWriteRequest.class);
        verify(writes).write(captor.capture());
        assertEquals(
                Set.of(
                        "organization:" + ORG + " organization",
                        "organization:" + ORG + "#member viewer",
                        "user:" + ADMIN + " administrator"),
                relations(captor.getValue()));
        assertTrue(captor.getValue().tuples().stream()
                .allMatch(tuple -> tuple.object().equals("knowledge_space:" + created.id())));
    }

    @Test
    void aDepartmentScopedSpaceAlsoCarriesItsOrganizationalUnitLink() {
        when(departments.existsByIdAndOrganizationId(DEPT, ORG)).thenReturn(true);

        var created = service.create(
                ACTOR,
                "Sales Knowledge",
                KnowledgeSpaceAudienceMode.DEPARTMENT,
                DEPT,
                "request-1");

        var captor = ArgumentCaptor.forClass(RelationshipTupleWriteRequest.class);
        verify(writes).write(captor.capture());
        assertTrue(relations(captor.getValue())
                .contains("organizational_unit:" + DEPT + " organizational_unit"));
        assertEquals(DEPT, created.departmentId());
    }

    @Test
    void aNameThatDerivesAnExistingKeyIsRefusedBeforeAnythingIsWritten() {
        when(spaces.existsByOrganizationIdAndKey(ORG, "sales-knowledge")).thenReturn(true);

        assertThrows(
                KnowledgeSpaceKeyConflictException.class,
                () -> service.create(
                        ACTOR,
                        "  Sales   Knowledge  ",
                        KnowledgeSpaceAudienceMode.RESTRICTED_CUSTOM,
                        null,
                        "request-1"));

        verify(spaces, never()).saveAndFlush(any());
        verify(writes, never()).write(any());
    }

    /**
     * A space row whose grants were never written would be visible to nobody and manageable by
     * nobody, so an unapplied write has to take the row with it.
     */
    @Test
    void aRelationshipWriteThatWasNotAppliedFailsTheWholeCreation() {
        when(writes.write(any()))
                .thenReturn(RelationshipTupleWriteResult.indeterminate("OPENFGA_WRITE_TIMEOUT", POLICY));

        var failure = assertThrows(
                KnowledgeSpaceUnavailableException.class,
                () -> service.create(
                        ACTOR,
                        "Sales Knowledge",
                        KnowledgeSpaceAudienceMode.RESTRICTED_CUSTOM,
                        null,
                        "request-1"));

        assertTrue(failure.getMessage().contains("OPENFGA_WRITE_TIMEOUT"));
    }

    /**
     * The existence check is a read followed by a write, so two administrators naming the same
     * space at once both pass it. The unique constraint is what actually decides, and losing that
     * race is still a conflict rather than a server fault.
     */
    @Test
    void losingTheRaceOnAKeyIsAConflictRatherThanAServerFault() {
        when(spaces.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_knowledge_space_key"));

        var conflict = assertThrows(
                KnowledgeSpaceKeyConflictException.class,
                () -> service.create(
                        ACTOR,
                        "Sales Knowledge",
                        KnowledgeSpaceAudienceMode.RESTRICTED_CUSTOM,
                        null,
                        "request-1"));

        assertTrue(conflict.getMessage().contains("sales-knowledge"));
        verify(writes, never()).write(any());
    }

    /**
     * A page carrying a continuation token but no tuples advances neither the scan count nor the
     * cursor. Bounding only on tuples read would spin on it forever.
     */
    @Test
    void aStoreThatKeepsHandingBackAnEmptyPageStillTerminates() {
        KnowledgeSpace space = new KnowledgeSpace(
                ORG,
                KnowledgeSpaceAudienceMode.RESTRICTED_CUSTOM,
                null,
                "sales-knowledge",
                "Sales Knowledge");
        when(spaces.findByOrganizationIdOrderByName(ORG)).thenReturn(List.of(space));
        when(tuples.readObject(anyString(), anyInt(), any()))
                .thenReturn(RelationshipTuplePage.resolved(List.of(), "same-token-forever", POLICY));

        var listed = assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> service.list(ACTOR), "the grant listing must terminate");

        assertFalse(listed.getFirst().grantsComplete());
    }

    @Test
    void aNameWithNoLettersOrDigitsCannotDeriveAKey() {
        assertThrows(
                BusinessValidationException.class,
                () -> service.create(
                        ACTOR,
                        " --- ",
                        KnowledgeSpaceAudienceMode.RESTRICTED_CUSTOM,
                        null,
                        "request-1"));
        assertThrows(
                BusinessValidationException.class,
                () -> service.create(
                        ACTOR,
                        "  ",
                        KnowledgeSpaceAudienceMode.RESTRICTED_CUSTOM,
                        null,
                        "request-1"));
    }

    @Test
    void creatingASpaceRequiresTheOrganizationPermission() {
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.deny("NO_RELATIONSHIP", POLICY));

        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> service.create(
                        ACTOR,
                        "Sales Knowledge",
                        KnowledgeSpaceAudienceMode.RESTRICTED_CUSTOM,
                        null,
                        "request-1"));

        verify(spaces, never()).saveAndFlush(any());
    }

    @Test
    void grantingWritesOneTupleForTheNamedSubject() {
        UUID spaceId = givenSpace(null);

        when(departments.existsByIdAndOrganizationId(DEPT, ORG)).thenReturn(true);

        service.grant(ACTOR, spaceId, "viewer", KnowledgeSpaceSubject.department(DEPT), "request-1");

        var captor = ArgumentCaptor.forClass(RelationshipTupleWriteRequest.class);
        verify(writes).write(captor.capture());
        assertEquals(
                List.of(RelationshipTuple.of(
                        "organizational_unit:" + DEPT + "#member",
                        "viewer",
                        "knowledge_space:" + spaceId)),
                captor.getValue().tuples());

        InOrder ordering = inOrder(customViewers, writes);
        ordering.verify(customViewers).saveAndFlush(any());
        ordering.verify(writes).write(any());
    }

    @Test
    void revokingDeletesTheSameTupleGrantingWrote() {
        UUID spaceId = givenSpace(null);
        when(tuples.delete(any())).thenReturn(RelationshipTupleWriteResult.applied(POLICY));

        service.revoke(
                ACTOR,
                spaceId,
                "reviewer",
                KnowledgeSpaceSubject.role("knowledge-reviewer"),
                "request-1");

        var captor = ArgumentCaptor.forClass(RelationshipTupleWriteRequest.class);
        verify(tuples).delete(captor.capture());
        assertEquals(
                List.of(RelationshipTuple.of(
                        "organization:" + ORG + "#knowledge_reviewer",
                        "reviewer",
                        "knowledge_space:" + spaceId)),
                captor.getValue().tuples());
    }

    @Test
    void aManagedAudienceRejectsOrdinaryViewerMutationButAllowsDriftRemoval() {
        UUID spaceId = givenSpace(DEPT);
        when(departments.existsByIdAndOrganizationId(DEPT, ORG)).thenReturn(true);
        when(tuples.delete(any())).thenReturn(RelationshipTupleWriteResult.applied(POLICY));

        assertThrows(
                BusinessValidationException.class,
                () -> service.revoke(
                        ACTOR,
                        spaceId,
                        "viewer",
                        KnowledgeSpaceSubject.department(DEPT),
                        "request-1"));

        service.revoke(
                ACTOR,
                spaceId,
                "viewer",
                KnowledgeSpaceSubject.organization(),
                "request-2");

        var captor = ArgumentCaptor.forClass(RelationshipTupleWriteRequest.class);
        verify(tuples).delete(captor.capture());
        assertEquals(
                List.of(RelationshipTuple.of(
                        "organization:" + ORG + "#member",
                        "viewer",
                        "knowledge_space:" + spaceId)),
                captor.getValue().tuples());
    }

    /**
     * {@code can_*} permissions are computed, and the structural links are creation-time facts. A
     * grant endpoint that accepted either would let an administrator author something the model
     * never intended to be stored.
     */
    @Test
    void onlyTheFourAclRelationsMayBeGranted() {
        UUID spaceId = givenSpace(null);
        var everyone = KnowledgeSpaceSubject.organization();

        for (String relation : List.of("organization", "organizational_unit", "can_view", "owner")) {
            assertThrows(
                    BusinessValidationException.class,
                    () -> service.grant(ACTOR, spaceId, relation, everyone, "request-1"),
                    relation + " must not be grantable");
        }
        verify(writes, never()).write(any());
    }

    /**
     * These are the type restrictions in {@code model.fga}, asserted here so a grant that OpenFGA
     * would reject is refused before it is attempted. Organization-wide reading is selected by
     * the Space mode rather than authored as an ordinary grant, and reviewing takes a unit's
     * managers rather than its members.
     */
    @Test
    void theGrantTableMatchesTheAuthorizationModelsTypeRestrictions() {
        assertEquals(
                Map.of(
                        "viewer", Set.of(Kind.DEPARTMENT, Kind.USER),
                        "contributor", Set.of(Kind.DEPARTMENT, Kind.ROLE, Kind.USER),
                        "reviewer", Set.of(Kind.DEPARTMENT_MANAGERS, Kind.ROLE, Kind.USER),
                        "administrator", Set.of(Kind.USER)),
                service.grantOptions());
    }

    @Test
    void roleGrantsProjectOnlyOrganizationRolesAcceptedByTheModel() {
        UUID spaceId = givenSpace(null);

        service.grant(
                ACTOR,
                spaceId,
                "contributor",
                KnowledgeSpaceSubject.role("knowledge-contributor"),
                "request-1");
        assertThrows(
                BusinessValidationException.class,
                () -> service.grant(
                        ACTOR,
                        spaceId,
                        "contributor",
                        KnowledgeSpaceSubject.role("organization-admin"),
                        "request-2"));

        var captor = ArgumentCaptor.forClass(RelationshipTupleWriteRequest.class);
        verify(writes).write(captor.capture());
        assertEquals(
                List.of(RelationshipTuple.of(
                        "organization:" + ORG + "#knowledge_contributor",
                        "contributor",
                        "knowledge_space:" + spaceId)),
                captor.getValue().tuples());
    }

    @Test
    void aSubjectShapeTheRelationDoesNotAcceptIsRefusedBeforeTheStoreSeesIt() {
        UUID spaceId = givenSpace(null);
        when(departments.existsByIdAndOrganizationId(DEPT, ORG)).thenReturn(true);

        // The model accepts organizational_unit#manager for reviewer, not #member.
        var refusal = assertThrows(
                BusinessValidationException.class,
                () -> service.grant(
                        ACTOR, spaceId, "reviewer", KnowledgeSpaceSubject.department(DEPT), "request-1"));
        assertTrue(refusal.getMessage().contains("DEPARTMENT_MANAGERS"));

        // And administering is never handed to a whole organization.
        assertThrows(
                BusinessValidationException.class,
                () -> service.grant(
                        ACTOR,
                        spaceId,
                        "administrator",
                        KnowledgeSpaceSubject.organization(),
                        "request-1"));

        verify(writes, never()).write(any());
    }

    @Test
    void aUnitsManagersMayBeMadeReviewers() {
        UUID spaceId = givenSpace(null);
        when(departments.existsByIdAndOrganizationId(DEPT, ORG)).thenReturn(true);

        service.grant(
                ACTOR, spaceId, "reviewer", KnowledgeSpaceSubject.departmentManagers(DEPT), "request-1");

        var captor = ArgumentCaptor.forClass(RelationshipTupleWriteRequest.class);
        verify(writes).write(captor.capture());
        assertEquals(
                List.of(RelationshipTuple.of(
                        "organizational_unit:" + DEPT + "#manager",
                        "reviewer",
                        "knowledge_space:" + spaceId)),
                captor.getValue().tuples());
    }

    /**
     * The organization in a subject reference comes from the authenticated actor, so the shapes a
     * caller can influence — a department and a user — are the ones that must be checked against
     * that organization before a tuple names them.
     */
    @Test
    void aSubjectFromAnotherOrganizationCannotBeGranted() {
        UUID spaceId = givenSpace(null);
        when(departments.existsByIdAndOrganizationId(DEPT, ORG)).thenReturn(false);
        AppUser foreign = mock(AppUser.class);
        when(foreign.getOrganizationId())
                .thenReturn(UUID.fromString("99999999-9999-4999-8999-999999999999"));
        when(users.findById(OTHER_ORG_USER)).thenReturn(Optional.of(foreign));

        assertThrows(
                KnowledgeResourceNotFoundException.class,
                () -> service.grant(
                        ACTOR, spaceId, "viewer", KnowledgeSpaceSubject.department(DEPT), "request-1"));
        assertThrows(
                KnowledgeResourceNotFoundException.class,
                () -> service.grant(
                        ACTOR,
                        spaceId,
                        "viewer",
                        KnowledgeSpaceSubject.user(OTHER_ORG_USER),
                        "request-1"));

        verify(writes, never()).write(any());
    }

    @Test
    void aSpaceInAnotherOrganizationIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(spaces.findByIdAndOrganizationIdAndActiveTrue(unknown, ORG)).thenReturn(Optional.empty());

        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> service.grant(
                        ACTOR, unknown, "viewer", KnowledgeSpaceSubject.organization(), "request-1"));
    }

    /** A partial list of who has access must not read as the whole of it. */
    @Test
    void aGrantListingThatCouldNotBeReadReportsItselfIncomplete() {
        KnowledgeSpace space = new KnowledgeSpace(
                ORG,
                KnowledgeSpaceAudienceMode.RESTRICTED_CUSTOM,
                null,
                "sales-knowledge",
                "Sales Knowledge");
        when(spaces.findByOrganizationIdOrderByName(ORG)).thenReturn(List.of(space));
        when(tuples.readObject(anyString(), anyInt(), any()))
                .thenReturn(RelationshipTuplePage.indeterminate("OPENFGA_READ_TIMEOUT", POLICY));

        var listed = service.list(ACTOR);

        assertEquals(1, listed.size());
        assertFalse(listed.getFirst().grantsComplete());
        assertTrue(listed.getFirst().grants().isEmpty());
    }

    /** Structural links are not access, so a listing of who can read must not present them as such. */
    @Test
    void aListingReportsAclGrantsAndLeavesStructuralLinksOut() {
        KnowledgeSpace space = new KnowledgeSpace(
                ORG,
                KnowledgeSpaceAudienceMode.ORGANIZATION,
                null,
                "sales-knowledge",
                "Sales Knowledge");
        String object = "knowledge_space:" + space.getId();
        when(spaces.findByOrganizationIdOrderByName(ORG)).thenReturn(List.of(space));
        when(tuples.readObject(eq(object), anyInt(), any()))
                .thenReturn(RelationshipTuplePage.resolved(
                        List.of(
                                RelationshipTuple.of("organization:" + ORG, "organization", object),
                                RelationshipTuple.of("organization:" + ORG + "#member", "viewer", object),
                                RelationshipTuple.of("user:" + ADMIN, "administrator", object)),
                        null,
                        POLICY));

        var grants = service.list(ACTOR).getFirst().grants();

        assertEquals(
                Set.of(
                        "viewer organization:" + ORG + "#member",
                        "administrator user:" + ADMIN),
                grants.stream()
                        .map(grant -> grant.relation() + " " + grant.subject())
                        .collect(Collectors.toSet()));
        assertTrue(grants.stream().allMatch(KnowledgeSpaceAdministration.Grant::effective));
    }

    @Test
    void aViewerTupleOutsideTheManagedAudienceIsReportedAsIneffectiveDrift() {
        KnowledgeSpace space = new KnowledgeSpace(
                ORG,
                KnowledgeSpaceAudienceMode.DEPARTMENT,
                DEPT,
                "sales-knowledge",
                "Sales Knowledge");
        String object = "knowledge_space:" + space.getId();
        when(spaces.findByOrganizationIdOrderByName(ORG)).thenReturn(List.of(space));
        when(tuples.readObject(eq(object), anyInt(), any()))
                .thenReturn(RelationshipTuplePage.resolved(
                        List.of(
                                RelationshipTuple.of(
                                        "organizational_unit:" + DEPT + "#member", "viewer", object),
                                RelationshipTuple.of(
                                        "organization:" + ORG + "#member", "viewer", object)),
                        null,
                        POLICY));

        var grants = service.list(ACTOR).getFirst().grants();

        assertEquals(2, grants.size());
        assertTrue(grants.stream()
                .filter(grant -> grant.subject().startsWith("organizational_unit:"))
                .allMatch(KnowledgeSpaceAdministration.Grant::effective));
        assertTrue(grants.stream()
                .filter(grant -> grant.subject().startsWith("organization:"))
                .noneMatch(KnowledgeSpaceAdministration.Grant::effective));
    }

    private UUID givenSpace(UUID departmentId) {
        KnowledgeSpace space = new KnowledgeSpace(
                ORG,
                departmentId == null
                        ? KnowledgeSpaceAudienceMode.RESTRICTED_CUSTOM
                        : KnowledgeSpaceAudienceMode.DEPARTMENT,
                departmentId,
                "sales-knowledge",
                "Sales Knowledge");
        when(spaces.findByIdAndOrganizationIdAndActiveTrue(space.getId(), ORG))
                .thenReturn(Optional.of(space));
        return space.getId();
    }

    private static Set<String> relations(RelationshipTupleWriteRequest request) {
        return request.tuples().stream()
                .map(tuple -> tuple.user() + " " + tuple.relation())
                .collect(Collectors.toSet());
    }
}
