package com.orgmemory.worker.ingestion;

/**
 * Whitespace policy, chosen per block kind. Prose may be collapsed freely; a
 * table cell must not be, because its boundaries are the structure.
 */
final class BlockText {

    private BlockText() {
    }

    /** Collapses prose whitespace, keeping paragraph breaks. */
    static String prose(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    /**
     * Flattens one cell to a single line. A cell may legitimately contain line
     * breaks, but a row is one line, so they become spaces here rather than
     * splitting the row in two.
     */
    static String cell(String text) {
        return text.replaceAll("[\\s\\u00a0]+", " ").strip();
    }
}
