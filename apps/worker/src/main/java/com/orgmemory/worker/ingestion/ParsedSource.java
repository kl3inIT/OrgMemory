package com.orgmemory.worker.ingestion;

import java.util.List;

record ParsedSource(List<ParsedBlock> blocks, String detectedMediaType) {

    ParsedSource {
        blocks = List.copyOf(blocks);
    }
}
