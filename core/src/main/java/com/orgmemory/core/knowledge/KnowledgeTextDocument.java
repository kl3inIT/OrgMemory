package com.orgmemory.core.knowledge;

import java.util.Objects;

public record KnowledgeTextDocument(
        String content,
        Integer startPage,
        Integer endPage) {

    public KnowledgeTextDocument {
        content = Objects.requireNonNull(content, "content");
    }
}
