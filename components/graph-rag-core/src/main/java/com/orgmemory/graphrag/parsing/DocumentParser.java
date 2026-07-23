package com.orgmemory.graphrag.parsing;

import com.orgmemory.graphrag.processing.ProcessingComponentRef;

/** Effect boundary for parser engines. Implementations must return canonicalized text once. */
public interface DocumentParser {

    ProcessingComponentRef component();

    DocumentParseResult parse(DocumentParseRequest request);
}
