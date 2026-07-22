package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.ConnectorReconciler.ObjectOutcome;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.OrganizationRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ingests a connector crawl batch into the governed knowledge ledger. This is the dedicated
 * connector use case: it validates the staging envelope (fail closed on an unknown payload
 * version or unsupported source system), resolves identities once, then reconciles each
 * object and tombstone in its own transaction so a per-object failure is isolated from the
 * rest of the batch. It never widens access beyond the payload and grants nothing to unmapped
 * principals — the sealed ACL evidence it produces is intersected downstream with tenant,
 * OpenFGA policy, classification, and lifecycle at retrieval time.
 */
@Service
public class ConnectorIngestionService {

    private final ConnectorReconciler reconciler;
    private final OrganizationRepository organizations;
    private final AppUserRepository users;
    private final KnowledgeSpaceService knowledgeSpaces;
    private final TransactionTemplate perObjectTransaction;

    ConnectorIngestionService(
            ConnectorReconciler reconciler,
            OrganizationRepository organizations,
            AppUserRepository users,
            KnowledgeSpaceService knowledgeSpaces,
            PlatformTransactionManager transactionManager) {
        this.reconciler = reconciler;
        this.organizations = organizations;
        this.users = users;
        this.knowledgeSpaces = knowledgeSpaces;
        this.perObjectTransaction = new TransactionTemplate(transactionManager);
    }

    public ConnectorIngestionResult ingest(ConnectorCrawlBatch batch) {
        Objects.requireNonNull(batch, "batch");
        batch.versions().requireSupported();
        validateEnvelope(batch);

        ConnectorIngestionContext ctx = ConnectorIngestionContext.from(batch);
        ConnectorIdentityResolution resolution =
                perObjectTransaction.execute(status -> reconciler.resolveIdentities(ctx, batch));

        Map<String, ConnectorPermissionItem> permissions = new LinkedHashMap<>();
        for (ConnectorPermissionItem permission : batch.permissions()) {
            permissions.put(permission.externalObjectId(), permission);
        }

        List<String> materialized = new ArrayList<>();
        List<String> rotated = new ArrayList<>();
        List<String> contentDeferred = new ArrayList<>();
        List<String> retired = new ArrayList<>();
        List<ConnectorItemFailure> failures = new ArrayList<>();

        for (ConnectorContentItem content : batch.contents()) {
            try {
                ObjectOutcome outcome = perObjectTransaction.execute(status -> reconciler.reconcile(
                        ctx, content, permissions.get(content.externalObjectId()), resolution));
                switch (outcome) {
                    case MATERIALIZED -> materialized.add(content.externalObjectId());
                    case ROTATED -> rotated.add(content.externalObjectId());
                    case ROTATED_CONTENT_DEFERRED -> {
                        rotated.add(content.externalObjectId());
                        contentDeferred.add(content.externalObjectId());
                    }
                    default -> throw new IllegalStateException("unhandled outcome: " + outcome);
                }
            } catch (RuntimeException failure) {
                failures.add(new ConnectorItemFailure(content.externalObjectId(), reasonOf(failure)));
            }
        }

        for (ConnectorTombstone tombstone : batch.tombstones()) {
            try {
                boolean wasRetired = Boolean.TRUE.equals(
                        perObjectTransaction.execute(status -> reconciler.retire(ctx, tombstone)));
                if (wasRetired) {
                    retired.add(tombstone.externalObjectId());
                }
            } catch (RuntimeException failure) {
                failures.add(new ConnectorItemFailure(tombstone.externalObjectId(), reasonOf(failure)));
            }
        }

        return new ConnectorIngestionResult(materialized, rotated, contentDeferred, retired, failures);
    }

    private void validateEnvelope(ConnectorCrawlBatch batch) {
        if (!SlackConnectorProfile.supports(batch.sourceSystem())) {
            throw new IllegalArgumentException("Unsupported connector source system: " + batch.sourceSystem());
        }
        if (!organizations.existsById(batch.organizationId())) {
            throw new IllegalArgumentException("Organization does not exist");
        }
        AppUser actor = users.findById(batch.actorUserId())
                .orElseThrow(() -> new IllegalArgumentException("Connector actor user does not exist"));
        if (!actor.isActive() || !actor.getOrganizationId().equals(batch.organizationId())) {
            throw new IllegalArgumentException("Connector actor must be an active user in the organization");
        }
        knowledgeSpaces.requireInOrganization(batch.organizationId(), batch.knowledgeSpaceId());
    }

    private static String reasonOf(RuntimeException failure) {
        String message = failure.getMessage();
        String reason = message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
        return reason.length() > 500 ? reason.substring(0, 500) : reason;
    }
}
