package com.orgmemory.core.knowledge;

import com.orgmemory.core.authorization.AdministrativeTupleScope;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.RelationshipTuple;
import com.orgmemory.core.authorization.RelationshipTupleReconciliationPort;
import com.orgmemory.core.authorization.RelationshipTuplePage;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RelationshipTupleWriteRequest;
import com.orgmemory.core.authorization.RelationshipTupleWriteResult;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.DepartmentRepository;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating a Knowledge Space and deciding who reads it, at runtime.
 *
 * <p>Before this existed, every space came from a Flyway migration and every grant on one came
 * from the bootstrap tuple file, which made the concept the product is organized around the one
 * thing the product could not administer.
 *
 * <p>A grant here is not a way around the source system. A space grant satisfies one gate of the
 * retrieval chain; the mirrored source ACL still caps every read behind it, so granting a space
 * cannot reveal a document Slack or Drive would refuse.
 */
@Service
public class KnowledgeSpaceAdministrationService {

    private static final String RESOURCE_TYPE = "knowledge_space";
    private static final PermissionKey CAN_CREATE_SPACE = PermissionKey.of("can_create_knowledge_space");
    private static final PermissionKey CAN_MANAGE_ACL = PermissionKey.of("can_manage_acl");

    /**
     * Which subject shapes each relation accepts, mirroring the type restrictions in
     * {@code model.fga}.
     *
     * <p>The model does not accept the same subjects for every relation, and the asymmetry is
     * deliberate: reading is something a whole organization can be given, approving is not.
     * Reviewing takes {@code organizational_unit#manager} rather than {@code #member} for the same
     * reason.
     *
     * <p>OpenFGA would reject an unlisted combination anyway, so this exists to refuse it here —
     * with a message naming the shapes that would work — instead of surfacing a store validation
     * error. It is also what the grant-options endpoint publishes, so the browser offers exactly
     * the combinations that can succeed rather than discovering the rest by being turned down.
     */
    private static final Map<String, Set<KnowledgeSpaceSubject.Kind>> GRANTABLE_SUBJECTS = Map.of(
            "viewer",
                    Set.of(
                            KnowledgeSpaceSubject.Kind.ORGANIZATION,
                            KnowledgeSpaceSubject.Kind.DEPARTMENT,
                            KnowledgeSpaceSubject.Kind.ROLE,
                            KnowledgeSpaceSubject.Kind.USER),
            "contributor",
                    Set.of(
                            KnowledgeSpaceSubject.Kind.DEPARTMENT,
                            KnowledgeSpaceSubject.Kind.ROLE,
                            KnowledgeSpaceSubject.Kind.USER),
            "reviewer",
                    Set.of(
                            KnowledgeSpaceSubject.Kind.DEPARTMENT_MANAGERS,
                            KnowledgeSpaceSubject.Kind.ROLE,
                            KnowledgeSpaceSubject.Kind.USER),
            "administrator",
                    Set.of(KnowledgeSpaceSubject.Kind.ROLE, KnowledgeSpaceSubject.Kind.USER));

    /** The relations an administrator may author. Structural links are written at creation only. */
    private static final Set<String> GRANTABLE_RELATIONS = GRANTABLE_SUBJECTS.keySet();

    private static final int GRANT_PAGE_SIZE = 100;

    /**
     * How many tuples one space's listing will read before reporting itself incomplete. A space
     * with more grants than this is already past the point where a list explains anything.
     */
    private static final int MAXIMUM_GRANTS_SCANNED = 500;

    private final KnowledgeSpaceRepository spaces;
    private final DepartmentRepository departments;
    private final AppUserRepository users;
    private final RelationshipAuthorizationPort authorization;
    private final RelationshipTupleWritePort writes;
    private final RelationshipTupleReconciliationPort tuples;
    private final PermissionAuditService audit;

    KnowledgeSpaceAdministrationService(
            KnowledgeSpaceRepository spaces,
            DepartmentRepository departments,
            AppUserRepository users,
            RelationshipAuthorizationPort authorization,
            RelationshipTupleWritePort writes,
            RelationshipTupleReconciliationPort tuples,
            PermissionAuditService audit) {
        this.spaces = spaces;
        this.departments = departments;
        this.users = users;
        this.authorization = authorization;
        this.writes = writes;
        this.tuples = tuples;
        this.audit = audit;
    }

    /**
     * Creates a space and the relationships that make it reachable.
     *
     * <p>Three tuples, one of which is easy to miss. {@code organization} is structural:
     * {@code org_admin} resolves through {@code administrator from organization}, so without it a
     * fresh space is unreachable even to an organization administrator. {@code administrator} on
     * the creator is the accountability record — it is why no {@code created_by_user_id} column
     * exists, since the tuple already is that fact.
     *
     * <p>The row is flushed before the tuples are written, and a write that is not applied throws
     * so the row rolls back with it. The remaining window is a successful write followed by a
     * failed commit, which leaves tuples for a space with no row; those are inert, because every
     * read path resolves ids against {@code knowledge_spaces}. Writing tuples first would instead
     * leave grants against an id that was never issued, which nothing would ever clean up.
     */
    @Transactional
    public KnowledgeSpaceAdministration.Space create(
            CurrentActor actor, String name, UUID departmentId, String requestId) {
        Objects.requireNonNull(actor, "actor");
        requireOrganizationPermission(actor, CAN_CREATE_SPACE);

        String trimmedName = requireName(name);
        String key = KnowledgeSpaceKey.from(trimmedName);
        if (spaces.existsByOrganizationIdAndKey(actor.organizationId(), key)) {
            throw new KnowledgeSpaceKeyConflictException(
                    "A Knowledge Space with the key '" + key + "' already exists in this organization");
        }
        if (departmentId != null
                && !departments.existsByIdAndOrganizationId(departmentId, actor.organizationId())) {
            throw new IllegalArgumentException("Unknown department in this organization");
        }

        KnowledgeSpace space = spaces.saveAndFlush(
                new KnowledgeSpace(actor.organizationId(), departmentId, key, trimmedName));

        List<RelationshipTuple> created = new ArrayList<>();
        created.add(tuple(
                "organization:" + actor.organizationId(), "organization", space.getId()));
        created.add(tuple("user:" + actor.userId(), "administrator", space.getId()));
        if (departmentId != null) {
            created.add(tuple(
                    "organizational_unit:" + departmentId, "organizational_unit", space.getId()));
        }
        RelationshipTupleWriteResult result =
                writes.write(new RelationshipTupleWriteRequest(created));
        if (!result.applied()) {
            throw new KnowledgeSpaceUnavailableException(
                    "The Knowledge Space relationships were not applied: " + result.reasonCode());
        }

        record(actor, "CREATE_KNOWLEDGE_SPACE", space.getId(), result.policyVersion(), requestId);
        return describe(space, result.policyVersion());
    }

    /** Every space in the organization, each with the grants currently stored against it. */
    @Transactional(readOnly = true)
    public List<KnowledgeSpaceAdministration.Space> list(CurrentActor actor) {
        Objects.requireNonNull(actor, "actor");
        requireOrganizationPermission(actor, CAN_CREATE_SPACE);
        String policyVersion = tuples.policyVersion();
        return spaces.findByOrganizationIdOrderByName(actor.organizationId()).stream()
                .map(space -> describe(space, policyVersion))
                .toList();
    }

    @Transactional
    public void grant(
            CurrentActor actor,
            UUID knowledgeSpaceId,
            String relation,
            KnowledgeSpaceSubject subject,
            String requestId) {
        RelationshipTuple tuple = grantTuple(actor, knowledgeSpaceId, relation, subject);
        RelationshipTupleWriteResult result =
                writes.write(new RelationshipTupleWriteRequest(List.of(tuple)));
        if (!result.applied()) {
            throw new KnowledgeSpaceUnavailableException(
                    "The Knowledge Space grant was not applied: " + result.reasonCode());
        }
        record(
                actor,
                "GRANT_KNOWLEDGE_SPACE_ACCESS",
                knowledgeSpaceId,
                result.policyVersion(),
                requestId);
    }

    @Transactional
    public void revoke(
            CurrentActor actor,
            UUID knowledgeSpaceId,
            String relation,
            KnowledgeSpaceSubject subject,
            String requestId) {
        RelationshipTuple tuple = grantTuple(actor, knowledgeSpaceId, relation, subject);
        RelationshipTupleWriteResult result =
                tuples.delete(new RelationshipTupleWriteRequest(List.of(tuple)));
        if (!result.applied()) {
            throw new KnowledgeSpaceUnavailableException(
                    "The Knowledge Space revocation was not applied: " + result.reasonCode());
        }
        record(
                actor,
                "REVOKE_KNOWLEDGE_SPACE_ACCESS",
                knowledgeSpaceId,
                result.policyVersion(),
                requestId);
    }

    private RelationshipTuple grantTuple(
            CurrentActor actor, UUID knowledgeSpaceId, String relation, KnowledgeSpaceSubject subject) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        Objects.requireNonNull(subject, "subject");
        KnowledgeSpace space = spaces
                .findByIdAndOrganizationIdAndActiveTrue(knowledgeSpaceId, actor.organizationId())
                .orElseThrow(KnowledgeSpaceAdministrationService::accessDenied);
        requireSpacePermission(actor, space.getId());
        requireSubjectInOrganization(actor, subject);
        return tuple(
                subject.openFgaUser(actor.organizationId()),
                requireRelationAccepts(relation, subject.kind()),
                space.getId());
    }

    /** The subject shapes each relation accepts, for a caller building a grant form. */
    public Map<String, Set<KnowledgeSpaceSubject.Kind>> grantOptions() {
        return GRANTABLE_SUBJECTS;
    }

    /**
     * A subject may only name something inside the caller's own organization.
     *
     * <p>The organization reference is taken from the authenticated actor rather than the request,
     * so it cannot name a second tenant. A department and a user are supplied by the caller, so
     * both are checked against that organization before a tuple is written.
     */
    private void requireSubjectInOrganization(CurrentActor actor, KnowledgeSpaceSubject subject) {
        switch (subject.kind()) {
            case DEPARTMENT, DEPARTMENT_MANAGERS -> {
                if (!departments.existsByIdAndOrganizationId(subject.id(), actor.organizationId())) {
                    throw new IllegalArgumentException("Unknown department in this organization");
                }
            }
            case USER -> users.findById(subject.id())
                    .filter(candidate -> candidate.getOrganizationId().equals(actor.organizationId()))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown user in this organization"));
            case ORGANIZATION, ROLE -> {
                // ORGANIZATION resolves to the actor's own organization, and a role is a global
                // name whose assignees are themselves organization members.
            }
        }
    }

    private KnowledgeSpaceAdministration.Space describe(KnowledgeSpace space, String policyVersion) {
        List<KnowledgeSpaceAdministration.Grant> grants = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String object = RESOURCE_TYPE + ":" + space.getId();
        String continuationToken = null;
        int scanned = 0;
        boolean complete = true;
        do {
            RelationshipTuplePage page = tuples.readObject(object, GRANT_PAGE_SIZE, continuationToken);
            if (!page.resolved()) {
                return new KnowledgeSpaceAdministration.Space(
                        space.getId(),
                        space.getKey(),
                        space.getName(),
                        space.getDepartmentId(),
                        space.isActive(),
                        List.of(),
                        false,
                        page.policyVersion());
            }
            for (RelationshipTuple tuple : page.tuples()) {
                if (GRANTABLE_RELATIONS.contains(tuple.relation())
                        && seen.add(tuple.relation() + " " + tuple.user())) {
                    grants.add(new KnowledgeSpaceAdministration.Grant(tuple.relation(), tuple.user()));
                }
            }
            scanned += page.tuples().size();
            continuationToken = page.continuationToken();
            if (continuationToken != null && scanned >= MAXIMUM_GRANTS_SCANNED) {
                complete = false;
                break;
            }
        } while (continuationToken != null);

        return new KnowledgeSpaceAdministration.Space(
                space.getId(),
                space.getKey(),
                space.getName(),
                space.getDepartmentId(),
                space.isActive(),
                List.copyOf(grants),
                complete,
                policyVersion);
    }

    private void requireOrganizationPermission(CurrentActor actor, PermissionKey permission) {
        if (!authorization
                .check(new RelationshipAuthorizationQuery(
                        actor.principal(),
                        permission,
                        ResourceRef.of(actor.organizationId(), "organization", actor.organizationId())))
                .allowed()) {
            throw accessDenied();
        }
    }

    private void requireSpacePermission(CurrentActor actor, UUID knowledgeSpaceId) {
        if (!authorization
                .check(new RelationshipAuthorizationQuery(
                        actor.principal(),
                        CAN_MANAGE_ACL,
                        ResourceRef.of(actor.organizationId(), RESOURCE_TYPE, knowledgeSpaceId)))
                .allowed()) {
            throw accessDenied();
        }
    }

    private void record(
            CurrentActor actor,
            String operation,
            UUID knowledgeSpaceId,
            String policyVersion,
            String requestId) {
        audit.record(new PermissionAuditCommand(
                actor.organizationId(),
                actor.userId(),
                operation,
                RESOURCE_TYPE,
                knowledgeSpaceId.toString(),
                PermissionAuditDecision.ALLOW,
                "ADMINISTRATIVE_WRITE",
                policyVersion,
                requestId,
                null));
    }

    private static RelationshipTuple tuple(String user, String relation, UUID knowledgeSpaceId) {
        return AdministrativeTupleScope.require(
                RelationshipTuple.of(user, relation, RESOURCE_TYPE + ":" + knowledgeSpaceId));
    }

    private static String requireRelationAccepts(String value, KnowledgeSpaceSubject.Kind kind) {
        String normalized = Objects.requireNonNull(value, "relation").trim();
        Set<KnowledgeSpaceSubject.Kind> accepted = GRANTABLE_SUBJECTS.get(normalized);
        if (accepted == null) {
            throw new IllegalArgumentException(
                    "A Knowledge Space grant must be one of " + GRANTABLE_RELATIONS);
        }
        if (!accepted.contains(kind)) {
            throw new IllegalArgumentException(
                    "A " + normalized + " grant cannot name " + kind + "; it accepts " + accepted);
        }
        return normalized;
    }

    private static String requireName(String value) {
        String normalized = Objects.requireNonNull(value, "name").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("A Knowledge Space name is required");
        }
        if (normalized.length() > 255) {
            throw new IllegalArgumentException("A Knowledge Space name may be at most 255 characters");
        }
        return normalized;
    }

    private static OrgMemoryAccessDeniedException accessDenied() {
        return new OrgMemoryAccessDeniedException(
                "The current user is not authorized to administer this Knowledge Space");
    }
}
