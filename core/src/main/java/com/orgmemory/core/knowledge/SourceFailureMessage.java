package com.orgmemory.core.knowledge;

final class SourceFailureMessage {

    private static final int MAXIMUM_LENGTH = 512;

    private SourceFailureMessage() {
    }

    static String truncate(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.strip();
        return clean.length() <= MAXIMUM_LENGTH ? clean : clean.substring(0, MAXIMUM_LENGTH);
    }
}
