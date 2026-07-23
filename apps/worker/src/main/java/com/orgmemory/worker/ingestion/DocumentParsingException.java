package com.orgmemory.worker.ingestion;

final class DocumentParsingException extends RuntimeException {

    DocumentParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
