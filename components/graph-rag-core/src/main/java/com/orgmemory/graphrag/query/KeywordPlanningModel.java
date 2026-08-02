package com.orgmemory.graphrag.query;

import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Provider boundary for the LightRAG high/low keyword extraction call. */
public interface KeywordPlanningModel {

    ProcessingComponentRef component();

    default ProcessingComponentRef component(UUID organizationId) {
        return component();
    }

    default String modelRouteFingerprint(UUID organizationId) {
        return null;
    }

    KeywordPlan complete(String prompt);

    default KeywordPlan complete(UUID organizationId, String prompt) {
        return complete(prompt);
    }

    default Invocation resolve(UUID organizationId) {
        return new Invocation(
                component(organizationId),
                modelRouteFingerprint(organizationId),
                prompt -> complete(organizationId, prompt));
    }

    record Invocation(
            ProcessingComponentRef component,
            String modelRouteFingerprint,
            Function<String, KeywordPlan> completion) {

        public Invocation {
            Objects.requireNonNull(component, "component");
            Objects.requireNonNull(completion, "completion");
        }

        public KeywordPlan complete(String prompt) {
            return completion.apply(prompt);
        }
    }
}
