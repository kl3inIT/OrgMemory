package com.orgmemory.integrations.graphrag.springai;

import java.util.List;

record StructuredExtractionResponse(
        List<EntityItem> entities,
        List<RelationItem> relations) {

    record EntityItem(
            String reference,
            String name,
            String type,
            String description,
            Double confidence) {
    }

    record RelationItem(
            String sourceReference,
            String targetReference,
            String type,
            List<String> keywords,
            String description,
            String orientation,
            Double confidence) {
    }
}
