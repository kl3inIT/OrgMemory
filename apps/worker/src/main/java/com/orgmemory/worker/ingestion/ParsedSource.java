package com.orgmemory.worker.ingestion;

import java.util.List;
import org.springframework.ai.document.Document;

record ParsedSource(List<Document> documents, String detectedMediaType) {

    ParsedSource {
        documents = List.copyOf(documents);
    }
}
