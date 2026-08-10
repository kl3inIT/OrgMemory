package com.orgmemory.graphrag.parsing;

import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.Set;

/** Effect boundary for parser engines. Implementations must return canonicalized text once. */
public interface DocumentParser {

    ProcessingComponentRef component();

    /** Suffixes this adapter can parse. Product admission remains a separate policy. */
    default Set<String> supportedSuffixes() {
        return Set.of();
    }

    DocumentParseResult parse(DocumentParseRequest request);
}
