package com.orgmemory.graphrag.query;

import java.util.List;
import java.util.Objects;

/**
 * Executable LightRAG keyword planning semantics.
 *
 * <p>The model only completes the prompt. Prompt ownership, trusted bypass,
 * normalization, and the short-query fallback remain in the pure-Java core.
 */
public final class LightRagKeywordPlanner {

    private static final int SHORT_QUERY_FALLBACK_LIMIT = 50;

    private final KeywordPlanningModel model;
    private final String language;

    public LightRagKeywordPlanner(KeywordPlanningModel model, String language) {
        this.model = Objects.requireNonNull(model, "model");
        this.language = requireText(language, "language");
    }

    public KeywordPlan plan(String query, KeywordPlan trustedKeywords) {
        String normalizedQuery = requireText(query, "query");
        KeywordPlan candidate = trustedKeywords == null
                ? model.complete(prompt(normalizedQuery, language))
                : new KeywordPlan(
                        trustedKeywords.highLevel(),
                        trustedKeywords.lowLevel(),
                        KeywordPlan.Source.TRUSTED_CALLER);
        Objects.requireNonNull(candidate, "keyword plan");
        if (!candidate.empty()) {
            return candidate;
        }
        if (normalizedQuery.length() < SHORT_QUERY_FALLBACK_LIMIT) {
            return new KeywordPlan(
                    List.of(),
                    List.of(normalizedQuery),
                    KeywordPlan.Source.SHORT_QUERY_FALLBACK);
        }
        return KeywordPlan.empty(candidate.source());
    }

    static String prompt(String query, String language) {
        return """
                ---Role---
                You extract search keywords for a retrieval-augmented generation system.

                ---Goal---
                Return high-level concepts and low-level concrete entities from the user query.

                ---Constraints---
                - Return one JSON object and no other text.
                - Use exactly the keys "high_level_keywords" and "low_level_keywords".
                - Both values are arrays of concise strings.
                - Derive every keyword only from the user query.
                - Keep meaningful multi-word phrases intact and remove duplicates.
                - Write keywords in %s; preserve proper nouns in their original language.
                - For vague or nonsensical input, return both arrays empty.

                ---User Query---
                %s
                """.formatted(language, query);
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
