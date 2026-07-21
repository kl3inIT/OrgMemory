package com.orgmemory.core.knowledge;

public class KnowledgeAssetNotFoundException extends RuntimeException {

    public KnowledgeAssetNotFoundException() {
        super("Knowledge asset not found");
    }
}
