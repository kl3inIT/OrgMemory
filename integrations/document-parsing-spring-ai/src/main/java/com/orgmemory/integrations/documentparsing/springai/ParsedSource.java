package com.orgmemory.integrations.documentparsing.springai;

import java.util.List;

record ParsedSource(List<ParsedBlock> blocks, String detectedMediaType) {

    ParsedSource {
        blocks = List.copyOf(blocks);
    }
}
