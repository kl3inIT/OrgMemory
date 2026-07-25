package com.orgmemory.graphrag.multimodal;

import com.orgmemory.graphrag.chunking.EncodedText;
import com.orgmemory.graphrag.chunking.SourceSpan;
import com.orgmemory.graphrag.chunking.TextTokenizer;
import com.orgmemory.graphrag.parsing.CanonicalDocument;
import java.util.Objects;

/** Rebuilds LightRAG-compatible leading and trailing context from exact source spans. */
public final class MultimodalContextBuilder {

    public static final String VERSION = "orgmemory-context/v1";

    private final TextTokenizer tokenizer;

    public MultimodalContextBuilder(TextTokenizer tokenizer) {
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer");
    }

    public MultimodalSurroundingContext build(
            CanonicalDocument document,
            MultimodalSidecarItem item,
            int leadingTokenBudget,
            int trailingTokenBudget) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(item, "item");
        if (item.targetEndChar() > document.content().length()) {
            throw new IllegalArgumentException(
                    "sidecar target span must be inside canonical text");
        }
        if (leadingTokenBudget < 0 || trailingTokenBudget < 0) {
            throw new IllegalArgumentException("context token budgets must not be negative");
        }
        String before = document.content().substring(0, item.targetStartChar());
        String after = document.content().substring(item.targetEndChar());
        return new MultimodalSurroundingContext(
                item.headingPath(),
                takeTrailingTokens(before, leadingTokenBudget),
                takeLeadingTokens(after, trailingTokenBudget),
                item.caption(),
                item.footnotes(),
                VERSION);
    }

    private String takeTrailingTokens(String value, int budget) {
        if (value.isEmpty() || budget == 0) {
            return "";
        }
        EncodedText encoded = tokenizer.encode(value);
        int from = Math.max(0, encoded.size() - budget);
        if (from == encoded.size()) {
            return "";
        }
        SourceSpan span = encoded.sourceSpan(from, encoded.size());
        return value.substring(span.startChar(), span.endChar()).strip();
    }

    private String takeLeadingTokens(String value, int budget) {
        if (value.isEmpty() || budget == 0) {
            return "";
        }
        EncodedText encoded = tokenizer.encode(value);
        int to = Math.min(encoded.size(), budget);
        if (to == 0) {
            return "";
        }
        SourceSpan span = encoded.sourceSpan(0, to);
        return value.substring(span.startChar(), span.endChar()).strip();
    }
}
