package com.orgmemory.graphrag.summarization;

import java.util.Objects;

public record DescriptionSummaryResult(
        String summary,
        boolean modelUsed,
        int modelInvocations) {

    public DescriptionSummaryResult {
        summary = Objects.requireNonNull(summary, "summary").strip();
        if (summary.isEmpty()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        if (modelInvocations < 0 || modelUsed != (modelInvocations > 0)) {
            throw new IllegalArgumentException(
                    "model usage must match modelInvocations");
        }
    }
}
