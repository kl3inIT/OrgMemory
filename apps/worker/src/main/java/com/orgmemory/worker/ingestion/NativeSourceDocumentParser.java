package com.orgmemory.worker.ingestion;

import com.orgmemory.graphrag.parsing.DocumentParseRequest;
import com.orgmemory.graphrag.parsing.DocumentParseResult;
import com.orgmemory.graphrag.parsing.DocumentParser;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.HashMap;

/**
 * Local native parser route.
 *
 * <p>PR4 enriches its typed block IR from native sidecars. The engine identity is already
 * distinct so those semantics can be versioned without changing the legacy route.
 */
final class NativeSourceDocumentParser implements DocumentParser {

    static final ProcessingComponentRef COMPONENT =
            new ProcessingComponentRef("native", "spring-ai-2.0.0");
    private final SourceDocumentReader delegate;

    NativeSourceDocumentParser(SourceDocumentReader delegate) {
        this.delegate = delegate;
    }

    @Override
    public ProcessingComponentRef component() {
        return COMPONENT;
    }

    @Override
    public DocumentParseResult parse(DocumentParseRequest request) {
        DocumentParseResult parsed = delegate.parse(request);
        var metadata = new HashMap<>(parsed.metadata());
        metadata.put("engine", COMPONENT.toString());
        return new DocumentParseResult(
                parsed.document(),
                parsed.detectedMediaType(),
                metadata);
    }
}
