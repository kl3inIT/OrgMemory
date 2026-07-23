package com.orgmemory.graphrag.indexing;

import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceProvenance;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.ExtractedEntity;
import com.orgmemory.graphrag.model.ExtractedRelation;
import com.orgmemory.graphrag.model.ExtractionProfile;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Converts chunk-local model references into deterministic canonical graph identity while
 * retaining descriptions and confidence on evidence-scoped contributions.
 */
public final class GraphContributionAssembler {

    private GraphContributionAssembler() {
    }

    public static GraphRevisionContributions assemble(
            UUID organizationId,
            UUID knowledgeAssetId,
            UUID sourceRevisionId,
            UUID aclSnapshotId,
            long aclGeneration,
            long projectionGeneration,
            Instant extractedAt,
            List<ExtractedChunk> chunks) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        Objects.requireNonNull(aclSnapshotId, "aclSnapshotId");
        Objects.requireNonNull(extractedAt, "extractedAt");
        List<ExtractedChunk> orderedChunks = Objects.requireNonNull(chunks, "chunks").stream()
                .sorted(Comparator.comparing(ExtractedChunk::chunkId))
                .toList();
        requireOneProfile(orderedChunks);

        List<EntityContribution> entities = new ArrayList<>();
        List<RelationContribution> relations = new ArrayList<>();
        for (ExtractedChunk chunk : orderedChunks) {
            ChunkAssembly assembly = assembleChunk(
                    organizationId,
                    knowledgeAssetId,
                    sourceRevisionId,
                    aclSnapshotId,
                    aclGeneration,
                    projectionGeneration,
                    extractedAt,
                    chunk);
            entities.addAll(assembly.entities());
            relations.addAll(assembly.relations());
        }
        entities.sort(Comparator.comparing(EntityContribution::id));
        relations.sort(Comparator.comparing(RelationContribution::id));
        return new GraphRevisionContributions(
                organizationId,
                knowledgeAssetId,
                sourceRevisionId,
                projectionGeneration,
                entities,
                relations);
    }

    private static ChunkAssembly assembleChunk(
            UUID organizationId,
            UUID knowledgeAssetId,
            UUID sourceRevisionId,
            UUID aclSnapshotId,
            long aclGeneration,
            long projectionGeneration,
            Instant extractedAt,
            ExtractedChunk chunk) {
        ExtractionProfile profile = chunk.result().profile();
        Map<String, CanonicalEntity> entitiesByReference = new LinkedHashMap<>();
        Map<UUID, EntityAccumulator> entityGroups = new LinkedHashMap<>();
        for (ExtractedEntity extracted : chunk.result().entities()) {
            CanonicalEntity canonical = canonicalEntity(organizationId, extracted);
            entitiesByReference.put(extracted.reference(), canonical);
            entityGroups.computeIfAbsent(
                            canonical.id(),
                            ignored -> new EntityAccumulator(canonical))
                    .add(extracted);
        }

        List<EntityContribution> entityContributions = entityGroups.values().stream()
                .map(group -> group.toContribution(new EvidenceContext(
                        organizationId,
                        knowledgeAssetId,
                        sourceRevisionId,
                        chunk.chunkId(),
                        aclSnapshotId,
                        aclGeneration,
                        projectionGeneration,
                        profile,
                        extractedAt)))
                .sorted(Comparator.comparing(EntityContribution::id))
                .toList();

        Map<UUID, RelationAccumulator> relationGroups = new LinkedHashMap<>();
        for (ExtractedRelation extracted : chunk.result().relations()) {
            CanonicalEntity source = requiredEntity(
                    entitiesByReference, extracted.sourceReference());
            CanonicalEntity target = requiredEntity(
                    entitiesByReference, extracted.targetReference());
            CanonicalRelation canonical = canonicalRelation(
                    organizationId, source.id(), target.id(), extracted);
            relationGroups.computeIfAbsent(
                            canonical.id(),
                            ignored -> new RelationAccumulator(canonical))
                    .add(extracted);
        }
        List<RelationContribution> relationContributions = relationGroups.values().stream()
                .map(group -> group.toContribution(new EvidenceContext(
                        organizationId,
                        knowledgeAssetId,
                        sourceRevisionId,
                        chunk.chunkId(),
                        aclSnapshotId,
                        aclGeneration,
                        projectionGeneration,
                        profile,
                        extractedAt)))
                .sorted(Comparator.comparing(RelationContribution::id))
                .toList();
        return new ChunkAssembly(entityContributions, relationContributions);
    }

    private static CanonicalEntity canonicalEntity(
            UUID organizationId, ExtractedEntity extracted) {
        String normalizedName = normalizeName(extracted.name());
        String normalizedType = normalizeType(extracted.type());
        UUID id = deterministicId(
                organizationId + "|entity|" + normalizedType + "|" + normalizedName);
        return new CanonicalEntity(id, normalizedName, normalizedType);
    }

    private static CanonicalRelation canonicalRelation(
            UUID organizationId,
            UUID sourceEntityId,
            UUID targetEntityId,
            ExtractedRelation extracted) {
        String normalizedType = normalizeType(extracted.type());
        UUID canonicalSource = sourceEntityId;
        UUID canonicalTarget = targetEntityId;
        if (extracted.orientation() == RelationOrientation.UNDIRECTED
                && canonicalSource.compareTo(canonicalTarget) > 0) {
            canonicalSource = targetEntityId;
            canonicalTarget = sourceEntityId;
        }
        UUID id = deterministicId(organizationId
                + "|relation|"
                + extracted.orientation()
                + "|"
                + normalizedType
                + "|"
                + canonicalSource
                + "|"
                + canonicalTarget);
        return new CanonicalRelation(
                id,
                canonicalSource,
                canonicalTarget,
                normalizedType,
                extracted.orientation());
    }

    private static CanonicalEntity requiredEntity(
            Map<String, CanonicalEntity> entitiesByReference, String reference) {
        CanonicalEntity entity = entitiesByReference.get(reference);
        if (entity == null) {
            throw new IllegalArgumentException(
                    "relation endpoint does not resolve to an extracted entity");
        }
        return entity;
    }

    private static void requireOneProfile(List<ExtractedChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        ExtractionProfile expected = chunks.getFirst().result().profile();
        boolean mismatch = chunks.stream()
                .map(chunk -> chunk.result().profile())
                .anyMatch(profile -> !expected.equals(profile));
        if (mismatch) {
            throw new IllegalArgumentException(
                    "all extracted chunks in one revision must use the same profile");
        }
    }

    private static String normalizeName(String value) {
        return normalizeText(value).toLowerCase(Locale.ROOT);
    }

    private static String normalizeType(String value) {
        return normalizeText(value).toUpperCase(Locale.ROOT);
    }

    private static String normalizeText(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("canonical graph text must not be blank");
        }
        return normalized;
    }

    private static UUID deterministicId(String identity) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        digest[6] = (byte) ((digest[6] & 0x0f) | 0x80);
        digest[8] = (byte) ((digest[8] & 0x3f) | 0x80);
        ByteBuffer bytes = ByteBuffer.wrap(digest);
        return new UUID(bytes.getLong(), bytes.getLong());
    }

    private record EvidenceContext(
            UUID organizationId,
            UUID knowledgeAssetId,
            UUID sourceRevisionId,
            UUID chunkId,
            UUID aclSnapshotId,
            long aclGeneration,
            long projectionGeneration,
            ExtractionProfile profile,
            Instant extractedAt) {

        EvidenceProvenance provenance(double confidence) {
            return new EvidenceProvenance(
                    new EvidenceReference(
                            organizationId,
                            knowledgeAssetId,
                            sourceRevisionId,
                            chunkId,
                            aclSnapshotId,
                            aclGeneration),
                    projectionGeneration,
                    profile.provider(),
                    profile.model(),
                    profile.promptVersion(),
                    confidence,
                    extractedAt);
        }

        UUID contributionId(String kind, UUID canonicalId) {
            return deterministicId(sourceRevisionId + "|" + chunkId + "|" + kind + "|" + canonicalId);
        }
    }

    private static final class EntityAccumulator {

        private final CanonicalEntity entity;
        private final LinkedHashSet<String> descriptions = new LinkedHashSet<>();
        private double confidence;

        private EntityAccumulator(CanonicalEntity entity) {
            this.entity = entity;
        }

        private void add(ExtractedEntity extracted) {
            descriptions.add(normalizeText(extracted.description()));
            confidence = Math.max(confidence, extracted.confidence());
        }

        private EntityContribution toContribution(EvidenceContext context) {
            return new EntityContribution(
                    context.contributionId("entity", entity.id()),
                    entity,
                    String.join("\n", descriptions.stream().sorted().toList()),
                    context.provenance(confidence));
        }
    }

    private static final class RelationAccumulator {

        private final CanonicalRelation relation;
        private final LinkedHashSet<String> descriptions = new LinkedHashSet<>();
        private final LinkedHashSet<String> keywords = new LinkedHashSet<>();
        private double confidence;

        private RelationAccumulator(CanonicalRelation relation) {
            this.relation = relation;
        }

        private void add(ExtractedRelation extracted) {
            descriptions.add(normalizeText(extracted.description()));
            extracted.keywords().stream()
                    .map(GraphContributionAssembler::normalizeText)
                    .forEach(keywords::add);
            confidence = Math.max(confidence, extracted.confidence());
        }

        private RelationContribution toContribution(EvidenceContext context) {
            return new RelationContribution(
                    context.contributionId("relation", relation.id()),
                    relation,
                    keywords.stream().sorted().toList(),
                    String.join("\n", descriptions.stream().sorted().toList()),
                    context.provenance(confidence));
        }
    }

    private record ChunkAssembly(
            List<EntityContribution> entities, List<RelationContribution> relations) {
    }
}
