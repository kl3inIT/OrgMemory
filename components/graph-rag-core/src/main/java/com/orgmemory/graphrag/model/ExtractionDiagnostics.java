package com.orgmemory.graphrag.model;

import java.util.List;
import java.util.Objects;

public record ExtractionDiagnostics(
        List<ExtractionRoundMetrics> rounds,
        GleaningOutcome gleaningOutcome) {

    public ExtractionDiagnostics {
        rounds = List.copyOf(Objects.requireNonNull(rounds, "rounds"));
        Objects.requireNonNull(gleaningOutcome, "gleaningOutcome");
    }

    public static ExtractionDiagnostics notProfiled() {
        return new ExtractionDiagnostics(List.of(), GleaningOutcome.DISABLED);
    }

    public enum GleaningOutcome {
        DISABLED,
        COMPLETED,
        SKIPPED_TOKEN_LIMIT
    }
}
