package com.orgmemory.graphrag.parsing;

import com.orgmemory.graphrag.processing.ProcessingComponentRef;

/** Internal parser used only when an immutable canonical parse is explicitly supplied. */
public final class ReuseParser implements DocumentParser {

    public static final ProcessingComponentRef COMPONENT =
            new ProcessingComponentRef("reuse", "1");

    @Override
    public ProcessingComponentRef component() {
        return COMPONENT;
    }

    @Override
    public DocumentParseResult parse(DocumentParseRequest request) {
        return new DocumentParseResult(
                request.reusableDocument().orElseThrow(
                        () -> new IllegalArgumentException(
                                "reuse parser requires a canonical document")),
                request.mediaType(),
                java.util.Map.of("parseStageSkipped", "true"));
    }
}
