package com.orgmemory.graphrag.parsing;

public final class ParserUnavailableException extends RuntimeException {

    public ParserUnavailableException(String parserId, String reason) {
        super("parser " + parserId + " is unavailable: " + reason);
    }
}
