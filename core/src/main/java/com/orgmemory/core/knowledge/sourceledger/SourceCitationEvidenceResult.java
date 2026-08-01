package com.orgmemory.core.knowledge.sourceledger;

import java.util.Objects;

/** Typed Source Ledger result that preserves why citation evidence is unavailable. */
public sealed interface SourceCitationEvidenceResult {

    record Available(SourceCitationEvidence evidence)
            implements SourceCitationEvidenceResult {

        public Available {
            Objects.requireNonNull(evidence, "evidence");
        }
    }

    record Unavailable(Reason reason)
            implements SourceCitationEvidenceResult {

        public Unavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }

    enum Reason {
        REVISION_NOT_CURRENT,
        BLOB_NOT_AVAILABLE
    }
}
