package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.authorization.AuthorizationOutcome;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Shared fail-closed organization entry gate for Search, Assistant and MCP. */
@Service
class KnowledgeSearchAuthorizationService {

    static final String POLICY_VERSION = "secure-retrieval-v1";
    private static final PermissionKey CAN_SEARCH_KNOWLEDGE =
            PermissionKey.of("can_search_knowledge");

    private final RelationshipAuthorizationPort authorization;
    private final PermissionAuditService audit;

    KnowledgeSearchAuthorizationService(
            RelationshipAuthorizationPort authorization,
            PermissionAuditService audit) {
        this.authorization = authorization;
        this.audit = audit;
    }

    String require(
            CurrentActor actor,
            String requestId,
            String query) {
        Objects.requireNonNull(actor, "actor");
        var decision = authorization.check(
                new RelationshipAuthorizationQuery(
                        actor.principal(),
                        CAN_SEARCH_KNOWLEDGE,
                        ResourceRef.of(
                                actor.organizationId(),
                                "organization",
                                actor.organizationId())));
        if (decision.allowed()) {
            return decision.policyVersion();
        }
        if (decision.outcome() == AuthorizationOutcome.INDETERMINATE) {
            throw unavailable(
                    actor,
                    requestId,
                    query,
                    decision.reasonCode(),
                    decision.policyVersion());
        }
        audit.record(command(
                actor,
                requestId,
                query,
                PermissionAuditDecision.DENY,
                "OPENFGA_SEARCH_DENIED",
                decision.policyVersion()));
        throw new OrgMemoryAccessDeniedException(
                "The current user does not have permission for this operation");
    }

    KnowledgeRetrievalUnavailableException unavailable(
            CurrentActor actor,
            String requestId,
            String query,
            String reason,
            String policyVersion) {
        audit.record(command(
                actor,
                requestId,
                query,
                PermissionAuditDecision.DENY,
                reason,
                policyVersion));
        return new KnowledgeRetrievalUnavailableException(
                "Secure knowledge retrieval is temporarily unavailable");
    }

    PermissionAuditCommand command(
            CurrentActor actor,
            String requestId,
            String query,
            PermissionAuditDecision decision,
            String reason,
            String policyVersion) {
        return new PermissionAuditCommand(
                actor.organizationId(),
                actor.userId(),
                "SEARCH",
                "KNOWLEDGE_SEARCH",
                actor.organizationId().toString(),
                decision,
                reason,
                policyVersion,
                requestId,
                query,
                null,
                null,
                policyVersion,
                null,
                null,
                null,
                null);
    }
}
