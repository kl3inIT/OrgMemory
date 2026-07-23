package com.orgmemory.graphrag.authorization;

import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceProvenance;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.RelationContribution;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Merges only evidence visible in one immutable authorization scope.
 *
 * <p>The result is safe to summarize or cache only with both returned
 * fingerprints. It must never be persisted as a global canonical description.
 */
public final class PermissionScopedGraphMerger {

    private PermissionScopedGraphMerger() {
    }

    public static PermissionScopedGraphView merge(
            AuthorizedEvidenceScope scope,
            List<EntityContribution> entityContributions,
            List<RelationContribution> relationContributions) {
        Objects.requireNonNull(scope, "scope");
        List<EntityContribution> visibleEntities = Objects
                .requireNonNull(entityContributions, "entityContributions")
                .stream()
                .filter(contribution -> visible(scope, contribution.provenance()))
                .toList();
        Set<UUID> visibleEntityIds = visibleEntities.stream()
                .map(contribution -> contribution.entity().id())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<RelationContribution> visibleRelations = Objects
                .requireNonNull(relationContributions, "relationContributions")
                .stream()
                .filter(contribution -> visible(scope, contribution.provenance()))
                .filter(contribution -> visibleEntityIds.contains(
                                contribution.relation().sourceEntityId())
                        && visibleEntityIds.contains(
                                contribution.relation().targetEntityId()))
                .toList();
        String authorizationFingerprint = scope.authorizationFingerprint();
        String projectionFingerprint =
                projectionFingerprint(visibleEntities, visibleRelations);

        Map<UUID, EntityAccumulator> entities = new LinkedHashMap<>();
        visibleEntities.stream()
                .sorted(Comparator.comparing(EntityContribution::id))
                .forEach(contribution -> entities
                        .computeIfAbsent(
                                contribution.entity().id(),
                                ignored -> new EntityAccumulator(
                                        contribution.entity()))
                        .add(contribution));
        Map<UUID, RelationAccumulator> relations = new LinkedHashMap<>();
        visibleRelations.stream()
                .sorted(Comparator.comparing(RelationContribution::id))
                .forEach(contribution -> relations
                        .computeIfAbsent(
                                contribution.relation().id(),
                                ignored -> new RelationAccumulator(
                                        contribution.relation()))
                        .add(contribution));
        return new PermissionScopedGraphView(
                authorizationFingerprint,
                projectionFingerprint,
                entities.values().stream()
                        .map(accumulator -> accumulator.view(
                                authorizationFingerprint, projectionFingerprint))
                        .sorted(Comparator.comparing(view -> view.entity().id()))
                        .toList(),
                relations.values().stream()
                        .map(accumulator -> accumulator.view(
                                entities,
                                authorizationFingerprint,
                                projectionFingerprint))
                        .sorted(Comparator.comparing(view -> view.relation().id()))
                        .toList());
    }

    private static boolean visible(
            AuthorizedEvidenceScope scope,
            EvidenceProvenance provenance) {
        return scope.includes(
                provenance.organizationId(),
                provenance.knowledgeAssetId());
    }

    private static String projectionFingerprint(
            List<EntityContribution> entities,
            List<RelationContribution> relations) {
        MessageDigest digest = sha256();
        java.util.stream.Stream.concat(
                        entities.stream().map(PermissionScopedGraphMerger::projectionIdentity),
                        relations.stream().map(PermissionScopedGraphMerger::projectionIdentity))
                .sorted()
                .forEach(value -> update(digest, value));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String projectionIdentity(EntityContribution contribution) {
        MessageDigest digest = sha256();
        update(digest, "entity");
        update(digest, contribution.id().toString());
        update(digest, contribution.entity().id().toString());
        update(digest, contribution.entity().normalizedName());
        update(digest, contribution.type());
        update(digest, contribution.description());
        updateProvenance(digest, contribution.provenance());
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String projectionIdentity(RelationContribution contribution) {
        MessageDigest digest = sha256();
        update(digest, "relation");
        update(digest, contribution.id().toString());
        update(digest, contribution.relation().id().toString());
        update(digest, contribution.relation().sourceEntityId().toString());
        update(digest, contribution.relation().targetEntityId().toString());
        update(digest, contribution.relation().orientation().name());
        update(digest, contribution.type());
        contribution.keywords().stream().sorted().forEach(value -> update(digest, value));
        update(digest, contribution.description());
        update(digest, Double.toHexString(contribution.weight()));
        updateProvenance(digest, contribution.provenance());
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void updateProvenance(
            MessageDigest digest,
            EvidenceProvenance provenance) {
        update(digest, provenance.organizationId().toString());
        update(digest, provenance.knowledgeAssetId().toString());
        update(digest, provenance.sourceRevisionId().toString());
        update(digest, provenance.chunkId().toString());
        update(digest, provenance.aclSnapshotId().toString());
        update(digest, Long.toString(provenance.aclGeneration()));
        update(digest, Long.toString(provenance.projectionGeneration()));
        update(digest, provenance.extractorProvider());
        update(digest, provenance.extractorModel());
        update(digest, provenance.promptVersion());
        update(digest, provenance.extractionProfileFingerprint());
        update(digest, Double.toHexString(provenance.confidence()));
        update(digest, provenance.extractedAt().toString());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static void requireSameEntity(
            CanonicalEntity expected,
            CanonicalEntity candidate) {
        if (!expected.equals(candidate)) {
            throw new IllegalArgumentException(
                    "one canonical entity id resolved to conflicting identities");
        }
    }

    private static void requireSameRelation(
            CanonicalRelation expected,
            CanonicalRelation candidate) {
        if (!expected.equals(candidate)) {
            throw new IllegalArgumentException(
                    "one canonical relation id resolved to conflicting identities");
        }
    }

    private static final class EntityAccumulator {

        private final CanonicalEntity entity;
        private final Set<String> types = new LinkedHashSet<>();
        private final Set<String> descriptions = new LinkedHashSet<>();
        private final List<EvidenceReference> evidence = new ArrayList<>();
        private double confidence;

        private EntityAccumulator(CanonicalEntity entity) {
            this.entity = entity;
        }

        private void add(EntityContribution contribution) {
            requireSameEntity(entity, contribution.entity());
            types.add(contribution.type());
            descriptions.add(contribution.description());
            evidence.add(contribution.provenance().evidence());
            confidence = Math.max(confidence, contribution.provenance().confidence());
        }

        private PermissionScopedGraphView.EntityView view(
                String authorizationFingerprint,
                String projectionFingerprint) {
            return new PermissionScopedGraphView.EntityView(
                    entity,
                    types.stream().sorted().toList(),
                    descriptions.stream().sorted().toList(),
                    evidence.stream()
                            .distinct()
                            .sorted(Comparator.comparing(EvidenceReference::sourceRevisionId)
                                    .thenComparing(reference -> Objects.toString(
                                            reference.chunkId(), "")))
                            .toList(),
                    confidence,
                    authorizationFingerprint,
                    projectionFingerprint);
        }
    }

    private static final class RelationAccumulator {

        private final CanonicalRelation relation;
        private final Set<String> types = new LinkedHashSet<>();
        private final Set<String> keywords = new LinkedHashSet<>();
        private final Set<String> descriptions = new LinkedHashSet<>();
        private final List<EvidenceReference> evidence = new ArrayList<>();
        private double weight;
        private double confidence;

        private RelationAccumulator(CanonicalRelation relation) {
            this.relation = relation;
        }

        private void add(RelationContribution contribution) {
            requireSameRelation(relation, contribution.relation());
            types.add(contribution.type());
            keywords.addAll(contribution.keywords());
            descriptions.add(contribution.description());
            evidence.add(contribution.provenance().evidence());
            weight += contribution.weight();
            confidence = Math.max(confidence, contribution.provenance().confidence());
        }

        private PermissionScopedGraphView.RelationView view(
                Map<UUID, EntityAccumulator> entities,
                String authorizationFingerprint,
                String projectionFingerprint) {
            EntityAccumulator source = Objects.requireNonNull(
                    entities.get(relation.sourceEntityId()),
                    "visible relation source entity");
            EntityAccumulator target = Objects.requireNonNull(
                    entities.get(relation.targetEntityId()),
                    "visible relation target entity");
            return new PermissionScopedGraphView.RelationView(
                    relation,
                    source.entity.normalizedName(),
                    target.entity.normalizedName(),
                    types.stream().sorted().toList(),
                    keywords.stream().sorted().toList(),
                    descriptions.stream().sorted().toList(),
                    evidence.stream()
                            .distinct()
                            .sorted(Comparator.comparing(EvidenceReference::sourceRevisionId)
                                    .thenComparing(reference -> Objects.toString(
                                            reference.chunkId(), "")))
                            .toList(),
                    weight,
                    confidence,
                    authorizationFingerprint,
                    projectionFingerprint);
        }
    }
}
