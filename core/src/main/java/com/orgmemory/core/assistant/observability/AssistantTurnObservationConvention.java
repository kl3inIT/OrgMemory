package com.orgmemory.core.assistant.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;

/** Marker so an application can replace the naming without replacing the boundary. */
public interface AssistantTurnObservationConvention
        extends ObservationConvention<AssistantTurnObservationContext> {

    @Override
    default boolean supportsContext(Observation.Context context) {
        return context instanceof AssistantTurnObservationContext;
    }
}
