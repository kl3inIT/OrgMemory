package com.orgmemory.graphrag.summarization;

import com.orgmemory.graphrag.chunking.TextTokenizer;
import com.orgmemory.graphrag.port.DescriptionSummaryModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Token-bounded LightRAG-style iterative map/reduce summarization. */
public final class IterativeDescriptionSummarizer {

    private static final int MAXIMUM_REDUCE_PASSES = 32;
    private final TextTokenizer tokenizer;
    private final DescriptionSummaryModel model;

    public IterativeDescriptionSummarizer(
            TextTokenizer tokenizer,
            DescriptionSummaryModel model) {
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer");
        this.model = Objects.requireNonNull(model, "model");
    }

    public DescriptionSummaryResult summarize(
            ScopedDescriptionSet scopedDescriptions,
            DescriptionSummaryOptions options) {
        Objects.requireNonNull(scopedDescriptions, "scopedDescriptions");
        Objects.requireNonNull(options, "options");
        List<String> current = scopedDescriptions.descriptions().stream()
                .map(IterativeDescriptionSummarizer::sanitize)
                .distinct()
                .toList();
        if (current.size() == 1) {
            return new DescriptionSummaryResult(current.getFirst(), false, 0);
        }

        int invocations = 0;
        for (int pass = 0; pass < MAXIMUM_REDUCE_PASSES; pass++) {
            if (current.size() == 1) {
                return new DescriptionSummaryResult(
                        current.getFirst(),
                        invocations > 0,
                        invocations);
            }
            int totalTokens = tokenCount(current);
            if (totalTokens <= options.contextTokenLimit() || current.size() <= 2) {
                if (current.size() < options.forceModelAtFragmentCount()
                        && totalTokens < options.maximumOutputTokens()) {
                    return new DescriptionSummaryResult(
                            sanitize(String.join(options.separator(), current)),
                            invocations > 0,
                            invocations);
                }
                String summary = invoke(scopedDescriptions, options, current);
                return new DescriptionSummaryResult(
                        summary,
                        true,
                        invocations + 1);
            }

            List<List<String>> groups =
                    groups(current, options.contextTokenLimit());
            List<String> reduced = new ArrayList<>(groups.size());
            for (List<String> group : groups) {
                if (group.size() == 1) {
                    reduced.add(group.getFirst());
                } else {
                    reduced.add(invoke(scopedDescriptions, options, group));
                    invocations++;
                }
            }
            if (reduced.equals(current)) {
                throw new IllegalStateException(
                        "description summary reduction made no progress");
            }
            current = List.copyOf(reduced);
        }
        throw new IllegalStateException("description summary exceeded the reduce pass limit");
    }

    private String invoke(
            ScopedDescriptionSet scopedDescriptions,
            DescriptionSummaryOptions options,
            List<String> descriptions) {
        String summary = model.summarize(new DescriptionSummaryRequest(
                scopedDescriptions.subjectKind(),
                scopedDescriptions.subjectName(),
                descriptions,
                options.language(),
                options.maximumOutputTokens(),
                scopedDescriptions.authorizationFingerprint(),
                scopedDescriptions.projectionFingerprint()));
        return sanitize(summary);
    }

    private List<List<String>> groups(List<String> descriptions, int tokenLimit) {
        List<List<String>> groups = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentTokens = 0;
        for (String description : descriptions) {
            int descriptionTokens = tokenizer.count(description);
            if (!current.isEmpty()
                    && currentTokens + descriptionTokens > tokenLimit) {
                if (current.size() == 1 && !groups.isEmpty()) {
                    groups.getLast().addAll(current);
                } else {
                    groups.add(current);
                }
                current = new ArrayList<>();
                currentTokens = 0;
            }
            current.add(description);
            currentTokens = Math.addExact(currentTokens, descriptionTokens);
        }
        if (!current.isEmpty()) {
            if (current.size() == 1 && !groups.isEmpty()) {
                groups.getLast().addAll(current);
            } else {
                groups.add(current);
            }
        }
        return groups.stream().map(List::copyOf).toList();
    }

    private int tokenCount(List<String> descriptions) {
        int total = 0;
        for (String description : descriptions) {
            total = Math.addExact(total, tokenizer.count(description));
        }
        return total;
    }

    private static String sanitize(String value) {
        String source = Objects.requireNonNull(value, "value");
        StringBuilder result = new StringBuilder(source.length());
        source.codePoints()
                .filter(codePoint -> codePoint == '\n'
                        || codePoint == '\r'
                        || codePoint == '\t'
                        || codePoint >= 0x20)
                .forEach(result::appendCodePoint);
        String normalized = result.toString().strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("summary text must not be blank");
        }
        return normalized;
    }
}
