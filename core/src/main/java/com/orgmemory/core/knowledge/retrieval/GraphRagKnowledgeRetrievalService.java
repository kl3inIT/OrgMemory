package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.knowledge.search.PermissionAwareKnowledgeSearch;
import com.orgmemory.core.organization.CurrentActor;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Adapter-facing GraphRAG retrieval engine, present only when its runtime is configured. */
public interface GraphRagKnowledgeRetrievalService extends PermissionAwareKnowledgeSearch {

    /**
     * Runs the governed retrieval pipeline without answer generation for the
     * ADR 0020 restored-copy recall diagnostic.
     */
    RetrievalObservation observe(
            CurrentActor actor,
            String query,
            String requestId);

    record RetrievalObservation(
            List<RetrievedDocument> keywordSeededDocuments,
            List<RetrievedDocument> bypassDocuments,
            KeywordPlanSnapshot keywordPlan) {

        public RetrievalObservation {
            keywordSeededDocuments = List.copyOf(Objects.requireNonNull(
                    keywordSeededDocuments, "keywordSeededDocuments"));
            bypassDocuments = List.copyOf(Objects.requireNonNull(
                    bypassDocuments, "bypassDocuments"));
            Objects.requireNonNull(keywordPlan, "keywordPlan");
        }
    }

    record RetrievedDocument(
            UUID knowledgeAssetId,
            UUID sourceObjectId,
            String title) {

        public RetrievedDocument {
            Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
            Objects.requireNonNull(sourceObjectId, "sourceObjectId");
            title = Objects.requireNonNull(title, "title").strip();
            if (title.isEmpty()) {
                throw new IllegalArgumentException("title must not be blank");
            }
        }
    }

    record KeywordPlanSnapshot(
            List<String> highLevel,
            List<String> lowLevel,
            String source) {

        public KeywordPlanSnapshot {
            highLevel = List.copyOf(Objects.requireNonNull(highLevel, "highLevel"));
            lowLevel = List.copyOf(Objects.requireNonNull(lowLevel, "lowLevel"));
            source = Objects.requireNonNull(source, "source").strip();
            if (source.isEmpty()) {
                throw new IllegalArgumentException("source must not be blank");
            }
        }
    }
}
