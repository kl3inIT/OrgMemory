package com.orgmemory.graphrag.parsing;

import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.nio.charset.StandardCharsets;

/** Built-in parser for already textual evidence. */
public final class PassthroughParser implements DocumentParser {

    public static final ProcessingComponentRef COMPONENT =
            new ProcessingComponentRef("passthrough", "1");

    @Override
    public ProcessingComponentRef component() {
        return COMPONENT;
    }

    @Override
    public DocumentParseResult parse(DocumentParseRequest request) {
        return new DocumentParseResult(
                CanonicalDocument.text(new String(request.content(), StandardCharsets.UTF_8)),
                request.mediaType(),
                java.util.Map.of());
    }
}
