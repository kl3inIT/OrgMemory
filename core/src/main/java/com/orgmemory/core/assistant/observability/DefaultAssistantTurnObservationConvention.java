package com.orgmemory.core.assistant.observability;

import io.micrometer.common.KeyValues;

/**
 * Turns one assistant turn into tags, reading only {@link AssistantTurnEvent}.
 *
 * <p>That indirection is the whole payload guarantee on this surface. A convention handed the
 * prompt could put the prompt on a span; this one is handed a record that cannot hold a prompt,
 * so the worst it can publish is a wrong number.
 */
public class DefaultAssistantTurnObservationConvention
        implements AssistantTurnObservationConvention {

    public static final String NAME = "orgmemory.assistant.turn";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getContextualName(AssistantTurnObservationContext context) {
        return "assistant turn";
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(AssistantTurnObservationContext context) {
        AssistantTurnEvent event = context.toEvent();
        return KeyValues.of(
                AssistantTurnObservationDocumentation.LowCardinality.ENGINE
                        .withValue(event.engine().name().toLowerCase(java.util.Locale.ROOT)),
                AssistantTurnObservationDocumentation.LowCardinality.OUTCOME
                        .withValue(event.outcome().name().toLowerCase(java.util.Locale.ROOT)),
                AssistantTurnObservationDocumentation.LowCardinality.STARTED
                        .withValue(Boolean.toString(event.started())),
                AssistantTurnObservationDocumentation.LowCardinality.FAILURE_CODE
                        // "none" rather than absent: a tag that disappears on success would
                        // split the answered series in two on any backend that keys on the
                        // full tag set, and the panel that matters compares them.
                        .withValue(event.failureCode() == null ? "none" : event.failureCode()));
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(AssistantTurnObservationContext context) {
        AssistantTurnEvent event = context.toEvent();
        KeyValues values = KeyValues.of(
                AssistantTurnObservationDocumentation.HighCardinality.ORGANIZATION_ID
                        .withValue(event.organizationId().toString()),
                AssistantTurnObservationDocumentation.HighCardinality.EVIDENCE_COUNT
                        .withValue(Integer.toString(event.evidenceCount())),
                AssistantTurnObservationDocumentation.HighCardinality.CITATION_COUNT
                        .withValue(Integer.toString(event.citationCount())));
        if (!event.started()) {
            // Absent rather than zero: a turn that never emitted has no first token, and a
            // zero here would read as instantaneous on the very chart it would ruin.
            return values;
        }
        return values.and(
                AssistantTurnObservationDocumentation.HighCardinality.TIME_TO_FIRST_TOKEN_MS
                        .withValue(Long.toString(event.timeToFirstToken().toMillis())));
    }
}
