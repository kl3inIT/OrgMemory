package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.ConnectorReconciler.ObjectOutcome;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.OrganizationRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final ConnectorSourceRegistry sources;
    private final OrganizationRepository organizations;
    private final AppUserRepository users;
    private final KnowledgeSpaceService knowledgeSpaces;
    private final TransactionTemplate perObjectTransaction;

    ConnectorIngestionService(
            ConnectorReconciler reconciler,
            ConnectorSourceRegistry sources,
            OrganizationRepository organizations,
            AppUserRepository users,
            KnowledgeSpaceService knowledgeSpaces,
            PlatformTransactionManager transactionManager) {
        this.reconciler = reconciler;
        this.sources = sources;
        this.organizations = organizations;
        this.users = users;
        this.knowledgeSpaces = knowledgeSpaces;
        this.perObjectTransaction = new TransactionTemplate(transactionManager);
    }

    public ConnectorIngestionResult ingest(ConnectorCrawlBatch batch) {
        Objects.requireNonNull(batch, "batch");
        batch.versions().requireSupported();
        ConnectorSourceProfile profile = validateEnvelope(batch);

        ConnectorIngestionContext ctx = ConnectorIngestionContext.from(batch, profile);
        ConnectorIdentityResolution resolution =
                perObjectTransaction.execute(status -> reconciler.resolveIdentities(ctx, batch));

        Map<String, ConnectorPermissionItem> permissions = new LinkedHashMap<>();
        for (ConnectorPermissionItem permission : batch.permissions()) {
            permissions.put(permission.externalObjectId(), permission);
        }
        Set<String> contentObjectIds = new HashSet<>();
        for (ConnectorContentItem content : batch.contents()) {
            contentObjectIds.add(content.externalObjectId());
        }

        List<String> materialized = new ArrayList<>();
        List<String> rotated = new ArrayList<>();
        List<String> rematerialized = new ArrayList<>();
        List<String> retired = new ArrayList<>();
        List<ConnectorItemFailure> failures = new ArrayList<>();

        for (ConnectorContentItem content : batch.contents()) {
            try {
                ObjectOutcome outcome = perObjectTransaction.execute(status -> reconciler.reconcile(
                        ctx, content, permissions.get(content.externalObjectId()), resolution));
                recordOutcome(outcome, content.externalObjectId(), materialized, rotated, rematerialized);
            } catch (RuntimeException failure) {
                failures.add(new ConnectorItemFailure(content.externalObjectId(), reasonOf(failure)));
            }
        }

        // Objects that arrived only in the permissions payload (a permissions-only re-crawl on
        // its own cadence) reconcile their ACL without touching content.
        for (ConnectorPermissionItem permission : batch.permissions()) {
            if (contentObjectIds.contains(permission.externalObjectId())) {
                continue;
            }
            try {
                ObjectOutcome outcome = perObjectTransaction.execute(
                        status -> reconciler.reconcilePermissions(ctx, permission, resolution));
                recordOutcome(outcome, permission.externalObjectId(), materialized, rotated, rematerialized);
            } catch (RuntimeException failure) {
                failures.add(new ConnectorItemFailure(permission.externalObjectId(), reasonOf(failure)));
            }
        }

        for (ConnectorTombstone tombstone : batch.tombstones()) {
            retire(ctx, tombstone, retired, failures);
        }

        if (batch.crawlComplete()) {
            prune(ctx, batch, retired, failures);
        }

        return new ConnectorIngestionResult(materialized, rotated, rematerialized, retired, failures);
    }

    /**
     * Retires what a complete crawl did not mention: an object gone from the source leaves no
     * tombstone behind it, so the only evidence of a deletion is its absence.
     *
     * <p>Absence is only evidence when the crawl was exhaustive, which is why this runs solely
     * for a batch that declares itself complete. One case is refused even then: a complete crawl
     * that enumerated nothing at all while the connection has indexed objects. A workspace can
     * genuinely empty, but an adapter that lost its token, its scopes, or its channel membership
     * reports the same thing, and the two are indistinguishable from here. Retiring a whole
     * connection is the more expensive mistake, so it is not made on this evidence.
     */
    private void prune(
            ConnectorIngestionContext ctx,
            ConnectorCrawlBatch batch,
            List<String> retired,
            List<ConnectorItemFailure> failures) {
        Set<String> crawled = batch.crawledObjectIds();
        List<String> vanished = perObjectTransaction.execute(
                status -> reconciler.vanishedSince(ctx, crawled));
        if (vanished == null || vanished.isEmpty()) {
            return;
        }
        if (crawled.isEmpty()) {
            failures.add(new ConnectorItemFailure(
                    ctx.sourceConnectionKey(),
                    "refused to prune " + vanished.size()
                            + " indexed objects: the crawl claimed completeness but enumerated none"));
            return;
        }
        for (String externalObjectId : vanished) {
            retire(ctx, new ConnectorTombstone(externalObjectId), retired, failures);
        }
    }

    private void retire(
            ConnectorIngestionContext ctx,
            ConnectorTombstone tombstone,
            List<String> retired,
            List<ConnectorItemFailure> failures) {
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

    private static void recordOutcome(
            ObjectOutcome outcome,
            String externalObjectId,
            List<String> materialized,
            List<String> rotated,
            List<String> rematerialized) {
        switch (outcome) {
            case MATERIALIZED -> materialized.add(externalObjectId);
            case ROTATED -> rotated.add(externalObjectId);
            case REMATERIALIZED -> rematerialized.add(externalObjectId);
            default -> throw new IllegalStateException("unhandled outcome: " + outcome);
        }
    }

    private ConnectorSourceProfile validateEnvelope(ConnectorCrawlBatch batch) {
        // Resolving the profile is the check: a batch naming a system no adapter contributed
        // has nothing governing it and must not be allowed to write under that name.
        ConnectorSourceProfile profile = sources.require(batch.sourceSystem());
        if (!organizations.existsById(batch.organizationId())) {
            throw new IllegalArgumentException("Organization does not exist");
        }
        AppUser actor = users.findById(batch.actorUserId())
                .orElseThrow(() -> new IllegalArgumentException("Connector actor user does not exist"));
        if (!actor.isActive() || !actor.getOrganizationId().equals(batch.organizationId())) {
            throw new IllegalArgumentException("Connector actor must be an active user in the organization");
        }
        knowledgeSpaces.requireInOrganization(batch.organizationId(), batch.knowledgeSpaceId());
        return profile;
    }

    private static String reasonOf(RuntimeException failure) {
        String message = failure.getMessage();
        String reason = message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
        return reason.length() > 500 ? reason.substring(0, 500) : reason;
    }
}
