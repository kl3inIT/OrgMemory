package com.orgmemory.core.knowledge;

public class KnowledgeAssetPublicationUnavailableException extends RuntimeException {

    public KnowledgeAssetPublicationUnavailableException(String message) {
        super(message);
    }

    public KnowledgeAssetPublicationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
