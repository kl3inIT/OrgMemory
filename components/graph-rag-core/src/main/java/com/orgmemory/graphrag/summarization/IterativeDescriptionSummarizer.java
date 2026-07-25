package com.orgmemory.graphrag.summarization;

import com.orgmemory.graphrag.chunking.EncodedText;
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
        List<String> sanitized = scopedDescriptions.descriptions().stream()
                .map(IterativeDescriptionSummarizer::sanitize)
                .distinct()
                .toList();
        List<String> current =
                splitOversized(sanitized, options.contextTokenLimit());
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
            int totalTokens = tokenCount(current, options.separator());
            if (totalTokens <= options.contextTokenLimit()) {
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
                    groups(current, options.contextTokenLimit(), options.separator());
            boolean everyGroupIsSingleton =
                    groups.stream().allMatch(group -> group.size() == 1);
            List<String> reduced = new ArrayList<>(groups.size());
            for (List<String> group : groups) {
                if (group.size() == 1 && !everyGroupIsSingleton) {
                    reduced.add(group.getFirst());
                } else {
                    reduced.add(invoke(scopedDescriptions, options, group));
                    invocations++;
                }
            }
            reduced = splitOversized(reduced, options.contextTokenLimit());
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
        if (tokenCount(descriptions, options.separator())
                > options.contextTokenLimit()) {
            throw new IllegalArgumentException(
                    "description summary input exceeds the context token limit");
        }
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

    private List<List<String>> groups(
            List<String> descriptions,
            int tokenLimit,
            String separator) {
        List<List<String>> groups = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentTokens = 0;
        int separatorTokens = tokenizer.count(separator);
        for (String description : descriptions) {
            int descriptionTokens = tokenizer.count(description);
            int additionalTokens = current.isEmpty()
                    ? descriptionTokens
                    : Math.addExact(separatorTokens, descriptionTokens);
            if (!current.isEmpty()
                    && Math.addExact(currentTokens, additionalTokens) > tokenLimit) {
                groups.add(current);
                current = new ArrayList<>();
                currentTokens = 0;
                additionalTokens = descriptionTokens;
            }
            current.add(description);
            currentTokens = Math.addExact(currentTokens, additionalTokens);
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        return groups.stream().map(List::copyOf).toList();
    }

    private List<String> splitOversized(
            List<String> descriptions,
            int tokenLimit) {
        List<String> bounded = new ArrayList<>();
        for (String description : descriptions) {
            EncodedText encoded = tokenizer.encode(description);
            if (encoded.size() <= tokenLimit) {
                bounded.add(description);
                continue;
            }
            for (int start = 0; start < encoded.size(); start += tokenLimit) {
                int end = Math.min(encoded.size(), Math.addExact(start, tokenLimit));
                int startChar = start == 0
                        ? 0
                        : encoded.sourceSpan(start, start + 1).startChar();
                int endChar = end == encoded.size()
                        ? description.length()
                        : encoded.sourceSpan(end, end + 1).startChar();
                bounded.add(sanitize(description.substring(startChar, endChar)));
            }
        }
        return List.copyOf(bounded);
    }

    private int tokenCount(
            List<String> descriptions,
            String separator) {
        int total = 0;
        int separatorTokens = tokenizer.count(separator);
        for (int index = 0; index < descriptions.size(); index++) {
            if (index > 0) {
                total = Math.addExact(total, separatorTokens);
            }
            total = Math.addExact(total, tokenizer.count(descriptions.get(index)));
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
