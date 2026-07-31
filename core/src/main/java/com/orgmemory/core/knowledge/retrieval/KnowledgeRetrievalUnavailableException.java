package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;

public class KnowledgeRetrievalUnavailableException extends BusinessException {

    public KnowledgeRetrievalUnavailableException(String message) {
        super(
                BusinessErrorCategory.UNAVAILABLE,
                "knowledge.retrieval-unavailable",
                message);
    }

    public KnowledgeRetrievalUnavailableException(
            String message, Throwable cause) {
        super(
                BusinessErrorCategory.UNAVAILABLE,
                "knowledge.retrieval-unavailable",
                message,
                cause);
    }
}
