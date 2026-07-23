package com.orgmemory.core.knowledge;

public record KnowledgeAuthorizationConvergenceReport(
        int modelDriftDetected,
        int modelDriftRepaired,
        int tuplesScanned,
        int orphanTuplesDeleted,
        boolean complete,
        String reasonCode) {

    public KnowledgeAuthorizationConvergenceReport {
        if (modelDriftDetected < 0
                || modelDriftRepaired < 0
                || tuplesScanned < 0
                || orphanTuplesDeleted < 0) {
            throw new IllegalArgumentException("Convergence counters cannot be negative");
        }
        reasonCode = reasonCode == null || reasonCode.isBlank() ? null : reasonCode.trim();
        if ((complete && reasonCode != null) || (!complete && reasonCode == null)) {
            throw new IllegalArgumentException(
                    "Only an incomplete report must carry a failure reason");
        }
    }
}
