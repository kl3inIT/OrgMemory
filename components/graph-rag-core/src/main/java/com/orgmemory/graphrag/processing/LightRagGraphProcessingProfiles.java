package com.orgmemory.graphrag.processing;

import com.orgmemory.graphrag.extraction.LightRagExtractionPrompt;
import com.orgmemory.graphrag.indexing.GraphContributionAssembler;
import com.orgmemory.graphrag.indexing.LightRagEmbeddingPayloads;
import com.orgmemory.graphrag.model.ExtractionProfile;
import java.util.Objects;

/** Supported immutable graph-processing profiles derived from pinned LightRAG semantics. */
public final class LightRagGraphProcessingProfiles {

    public static final String ALGORITHM_VERSION =
            "lightrag-v1.5.4-orgmemory-secure-v1";

    private LightRagGraphProcessingProfiles() {
    }

    public static GraphProcessingProfile current(ExtractionProfile extractionProfile) {
        return GraphProcessingProfile.resolve(
                ALGORITHM_VERSION,
                Objects.requireNonNull(extractionProfile, "extractionProfile"),
                LightRagExtractionPrompt.templateSnapshot(),
                GraphContributionAssembler.MERGE_SEMANTICS_VERSION,
                LightRagEmbeddingPayloads.FORMAT_ID);
    }

    public static boolean supports(GraphProcessingProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return ALGORITHM_VERSION.equals(profile.algorithmVersion())
                && LightRagExtractionPrompt.templateSnapshot().strip()
                        .equals(profile.promptTemplate())
                && GraphContributionAssembler.MERGE_SEMANTICS_VERSION
                        .equals(profile.mergeSemanticsVersion())
                && LightRagEmbeddingPayloads.FORMAT_ID
                        .equals(profile.embeddingPayloadFormatVersion());
    }
}
