package com.orgmemory.core.assistant.observability;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * Declares the assistant turn observation the way Spring AI declares its own, so the names and
 * their cardinality are readable in one place rather than scattered across whoever calls
 * {@code lowCardinalityKeyValue}.
 *
 * <p>The name is OrgMemory's, not OpenTelemetry's, and that is deliberate. The GenAI semantic
 * conventions define {@code gen_ai.client.operation.time_to_first_chunk} measured from the
 * model call. This observation starts when the question arrives, so it also contains
 * permission-scoped retrieval — an OpenFGA batch check and a canonical ledger re-read — which
 * the user waits through and the semantic convention's metric excludes. Publishing a
 * turn-level measurement under the convention's name would misdescribe it and would collide
 * the day Spring AI emits the real one. Both are wanted: one isolates the model, this one
 * measures what the person watching a blank screen experiences.
 */
public enum AssistantTurnObservationDocumentation implements ObservationDocumentation {

    TURN {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>>
                getDefaultConvention() {
            return DefaultAssistantTurnObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinality.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinality.values();
        }
    };

    /**
     * Bounded enumerations only. Two engines times three outcomes is six series, and it stays
     * six however many tenants or questions arrive.
     */
    public enum LowCardinality implements KeyName {

        ENGINE {
            @Override
            public String asString() {
                return "engine";
            }
        },

        OUTCOME {
            @Override
            public String asString() {
                return "outcome";
            }
        },

        /** Whether a token ever reached the caller, which separates slow from never. */
        STARTED {
            @Override
            public String asString() {
                return "started";
            }
        };
    }

    /**
     * Span-only. These would grow a metric series per tenant, so nothing here may become a
     * meter tag — the same rule the GraphRAG meters are asserted against.
     */
    public enum HighCardinality implements KeyName {

        ORGANIZATION_ID {
            @Override
            public String asString() {
                return "organization_id";
            }
        },

        EVIDENCE_COUNT {
            @Override
            public String asString() {
                return "evidence_count";
            }
        },

        CITATION_COUNT {
            @Override
            public String asString() {
                return "citation_count";
            }
        },

        TIME_TO_FIRST_TOKEN_MS {
            @Override
            public String asString() {
                return "time_to_first_token_ms";
            }
        };
    }
}
