package com.orgmemory.integrations.graphrag.springai;

import java.util.List;

record StructuredExtractionResponse(
        List<EntityItem> entities,
        List<RelationItem> relationships) {

    record EntityItem(
            String name,
            String type,
            String description,
            Double confidence) {
    }

    record RelationItem(
            String source,
            String target,
            String type,
            List<String> keywords,
            String description,
            String orientation,
            Double confidence) {
    }
}
