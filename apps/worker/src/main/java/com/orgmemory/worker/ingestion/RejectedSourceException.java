package com.orgmemory.worker.ingestion;

final class RejectedSourceException extends RuntimeException {

    private final String code;

    RejectedSourceException(String code, String message) {
        super(message);
        this.code = code;
    }

    String code() {
        return code;
    }
}
