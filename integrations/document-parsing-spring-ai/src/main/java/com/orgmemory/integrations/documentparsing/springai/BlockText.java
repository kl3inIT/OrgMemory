package com.orgmemory.integrations.documentparsing.springai;

/** Whitespace normalization selected by canonical block kind. */
final class BlockText {

    private BlockText() {
    }

    static String prose(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    static String cell(String text) {
        return text.replaceAll("[\\s\\u00a0]+", " ").strip();
    }
}
