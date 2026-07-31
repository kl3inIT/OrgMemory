package com.orgmemory.core.knowledge.asset;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;

public class KnowledgeAssetPublicationUnavailableException extends BusinessException {

    public KnowledgeAssetPublicationUnavailableException(String message) {
        super(
                BusinessErrorCategory.UNAVAILABLE,
                "knowledge-publication.unavailable",
                message);
    }

    public KnowledgeAssetPublicationUnavailableException(String message, Throwable cause) {
        super(
                BusinessErrorCategory.UNAVAILABLE,
                "knowledge-publication.unavailable",
                message,
                cause);
    }
}
