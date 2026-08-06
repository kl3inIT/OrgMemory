package com.orgmemory.worker.ingestion;

import com.orgmemory.graphrag.parsing.DocumentBlockKind;
import java.util.Map;
import java.util.Objects;

/**
 * One block a reader extracted, before it is placed into canonical text. Spans
 * are assigned later, once every block's position is known.
 */
record ParsedBlock(
        DocumentBlockKind kind,
        String text,
        Integer headingLevel,
        Integer startPage,
        Integer endPage,
        Map<String, String> attributes) {

    ParsedBlock {
        Objects.requireNonNull(kind, "kind");
        text = Objects.requireNonNull(text, "text");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
    }

    static ParsedBlock paragraph(String text) {
        return new ParsedBlock(DocumentBlockKind.PARAGRAPH, text, null, null, null, Map.of());
    }

    static ParsedBlock paragraph(String text, Integer startPage, Integer endPage) {
        return new ParsedBlock(
                DocumentBlockKind.PARAGRAPH, text, null, startPage, endPage, Map.of());
    }

    static ParsedBlock heading(String text, int level) {
        return new ParsedBlock(DocumentBlockKind.HEADING, text, level, null, null, Map.of());
    }

    static ParsedBlock table(String text, boolean headerFromMarkup) {
        return new ParsedBlock(
                DocumentBlockKind.TABLE,
                text,
                null,
                null,
                null,
                Map.of("header", headerFromMarkup ? "markup" : "first-row"));
    }
}
