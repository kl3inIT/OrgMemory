package com.orgmemory.graphrag.multimodal;

import java.util.Objects;

/** Terminal analysis state. Job scheduling states such as pending are deliberately excluded. */
public sealed interface MultimodalAnalysisOutcome
        permits MultimodalAnalysisOutcome.Success,
                MultimodalAnalysisOutcome.Skipped,
                MultimodalAnalysisOutcome.Failure {

    String itemId();

    MultimodalModality modality();

    record Success(
            String itemId,
            MultimodalModality modality,
            MultimodalAnalysisContent content,
            MultimodalEvidenceScope evidenceScope,
            MultimodalSurroundingContext surroundingContext,
            MultimodalAnalysisRoute route,
            MultimodalAnalysisCacheKey cacheKey)
            implements MultimodalAnalysisOutcome {

        public Success {
            itemId = requireText(itemId, "itemId");
            Objects.requireNonNull(modality, "modality");
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(evidenceScope, "evidenceScope");
            Objects.requireNonNull(surroundingContext, "surroundingContext");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(cacheKey, "cacheKey");
            if (content instanceof MultimodalAnalysisContent.Image
                    && modality != MultimodalModality.IMAGE
                    || content instanceof MultimodalAnalysisContent.Table
                            && modality != MultimodalModality.TABLE
                    || content instanceof MultimodalAnalysisContent.Equation
                            && modality != MultimodalModality.EQUATION) {
                throw new IllegalArgumentException(
                        "analysis content does not match modality");
            }
        }
    }

    record Skipped(
            String itemId,
            MultimodalModality modality,
            String reasonCode,
            String detail)
            implements MultimodalAnalysisOutcome {

        public Skipped {
            itemId = requireText(itemId, "itemId");
            Objects.requireNonNull(modality, "modality");
            reasonCode = requireText(reasonCode, "reasonCode");
            detail = requireText(detail, "detail");
        }
    }

    record Failure(
            String itemId,
            MultimodalModality modality,
            String reasonCode,
            String detail,
            boolean transientFailure)
            implements MultimodalAnalysisOutcome {

        public Failure {
            itemId = requireText(itemId, "itemId");
            Objects.requireNonNull(modality, "modality");
            reasonCode = requireText(reasonCode, "reasonCode");
            detail = requireText(detail, "detail");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
