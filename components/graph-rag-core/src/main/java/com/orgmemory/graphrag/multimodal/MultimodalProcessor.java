package com.orgmemory.graphrag.multimodal;

import com.orgmemory.graphrag.parsing.CanonicalDocument;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fail-closed multimodal orchestration.
 *
 * <p>Only deterministic preflight rules may skip an item. Provider and schema failures remain
 * failures and block publication.
 */
public final class MultimodalProcessor {

    private static final Set<String> SUPPORTED_IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    private final MultimodalContextBuilder contextBuilder;
    private final MultimodalChunkAssembler chunkAssembler;
    private final Map<MultimodalAnalyzerRole, MultimodalAnalyzer> analyzers;

    public MultimodalProcessor(
            MultimodalContextBuilder contextBuilder,
            MultimodalChunkAssembler chunkAssembler,
            List<MultimodalAnalyzer> analyzers) {
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder");
        this.chunkAssembler = Objects.requireNonNull(chunkAssembler, "chunkAssembler");
        var byRole = new EnumMap<MultimodalAnalyzerRole, MultimodalAnalyzer>(
                MultimodalAnalyzerRole.class);
        for (MultimodalAnalyzer analyzer : Objects.requireNonNull(analyzers, "analyzers")) {
            Objects.requireNonNull(analyzer, "analyzer");
            if (byRole.putIfAbsent(analyzer.role(), analyzer) != null) {
                throw new IllegalArgumentException(
                        "only one analyzer may be registered per role");
            }
        }
        this.analyzers = Map.copyOf(byRole);
    }

    public MultimodalProcessingResult process(
            CanonicalDocument document,
            MultimodalSidecar sidecar,
            MultimodalEvidenceScope evidenceScope,
            String sourceRevisionContentSha256,
            String parserVersion,
            MultimodalProcessingProfile profile) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(sidecar, "sidecar");
        Objects.requireNonNull(evidenceScope, "evidenceScope");
        Objects.requireNonNull(profile, "profile");
        if (!sidecar.canonicalTextSha256().equals(document.contentSha256())) {
            throw new IllegalArgumentException(
                    "sidecar must reference the supplied canonical document");
        }

        var outcomes = new ArrayList<MultimodalAnalysisOutcome>();
        var chunks = new ArrayList<MultimodalDerivedChunk>();
        boolean publishable = true;
        for (MultimodalSidecarItem item : sidecar.items()) {
            MultimodalAnalysisOutcome deterministicSkip =
                    preflight(item, profile);
            if (deterministicSkip != null) {
                outcomes.add(deterministicSkip);
                if (profile.requiredModalities().contains(item.modality())) {
                    publishable = false;
                }
                continue;
            }

            MultimodalAnalysisRoute route = profile.routes().get(item.modality());
            MultimodalAnalyzer analyzer = analyzers.get(route.role());
            if (analyzer == null) {
                outcomes.add(new MultimodalAnalysisOutcome.Failure(
                        item.itemId(),
                        item.modality(),
                        "ANALYZER_UNAVAILABLE",
                        "No analyzer is configured for role " + route.role(),
                        false));
                publishable = false;
                continue;
            }
            MultimodalSurroundingContext context = contextBuilder.build(
                    document,
                    item,
                    profile.leadingContextTokens(),
                    profile.trailingContextTokens());
            MultimodalAnalysisCacheKey cacheKey = MultimodalAnalysisCacheKey.create(
                    evidenceScope,
                    sourceRevisionContentSha256,
                    parserVersion,
                    sidecar,
                    item,
                    context,
                    route,
                    profile);
            MultimodalAnalysisRequest request = new MultimodalAnalysisRequest(
                    evidenceScope, item, context, route, cacheKey);
            MultimodalAnalysisOutcome outcome = analyzer.analyze(request);
            outcome = validateAnalyzerOutcome(request, outcome);
            outcomes.add(outcome);
            if (outcome instanceof MultimodalAnalysisOutcome.Success success) {
                try {
                    chunks.add(chunkAssembler.assemble(
                            item, success, profile.chunkTokenBudget()));
                } catch (RuntimeException exception) {
                    outcomes.removeLast();
                    outcomes.add(new MultimodalAnalysisOutcome.Failure(
                            item.itemId(),
                            item.modality(),
                            "CHUNK_CONSTRUCTION_FAILED",
                            "Validated analysis could not be materialized",
                            false));
                    publishable = false;
                }
            } else {
                publishable = false;
            }
        }
        return new MultimodalProcessingResult(outcomes, chunks, publishable);
    }

    private static MultimodalAnalysisOutcome validateAnalyzerOutcome(
            MultimodalAnalysisRequest request,
            MultimodalAnalysisOutcome outcome) {
        if (outcome == null
                || !request.item().itemId().equals(outcome.itemId())
                || request.item().modality() != outcome.modality()
                || outcome instanceof MultimodalAnalysisOutcome.Skipped) {
            return new MultimodalAnalysisOutcome.Failure(
                    request.item().itemId(),
                    request.item().modality(),
                    "INVALID_ANALYZER_OUTCOME",
                    "Analyzer returned an invalid terminal outcome",
                    false);
        }
        if (outcome instanceof MultimodalAnalysisOutcome.Success success
                && (!success.evidenceScope().equals(request.evidenceScope())
                        || !success.cacheKey().equals(request.cacheKey())
                        || !success.route().equals(request.route())
                        || !success.surroundingContext()
                                .equals(request.surroundingContext()))) {
            return new MultimodalAnalysisOutcome.Failure(
                    request.item().itemId(),
                    request.item().modality(),
                    "PROVENANCE_MISMATCH",
                    "Analyzer changed immutable request provenance",
                    false);
        }
        return outcome;
    }

    private static MultimodalAnalysisOutcome preflight(
            MultimodalSidecarItem item,
            MultimodalProcessingProfile profile) {
        if (!profile.enabledModalities().contains(item.modality())) {
            return skipped(item, "MODALITY_DISABLED", "Modality is disabled by the profile");
        }
        if (!(item.payload() instanceof MultimodalPayload.Image image)) {
            return null;
        }
        MultimodalBinaryArtifact artifact = image.artifact();
        if (!SUPPORTED_IMAGE_TYPES.contains(artifact.mediaType())) {
            return skipped(
                    item,
                    "UNSUPPORTED_IMAGE_TYPE",
                    "Image media type is not supported");
        }
        if (artifact.byteSize() > profile.maximumImageBytes()) {
            return skipped(item, "IMAGE_TOO_LARGE", "Image exceeds the byte limit");
        }
        if (artifact.width().isPresent()
                && artifact.height().isPresent()
                && Math.multiplyFull(
                                artifact.width().getAsInt(),
                                artifact.height().getAsInt())
                        > profile.maximumImagePixels()) {
            return skipped(item, "IMAGE_TOO_MANY_PIXELS", "Image exceeds the pixel limit");
        }
        return null;
    }

    private static MultimodalAnalysisOutcome.Skipped skipped(
            MultimodalSidecarItem item,
            String reasonCode,
            String detail) {
        return new MultimodalAnalysisOutcome.Skipped(
                item.itemId(), item.modality(), reasonCode, detail);
    }
}
