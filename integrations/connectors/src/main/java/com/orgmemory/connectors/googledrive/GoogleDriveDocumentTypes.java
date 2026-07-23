package com.orgmemory.connectors.googledrive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which Drive files this adapter indexes, and how each becomes text.
 *
 * <p>Google's own formats are not files: a Doc has no bytes to download, only exports. Anything
 * already textual is downloaded as itself. Everything else — PDFs, images, archives, binaries —
 * is skipped, because extracting text from them is a parser problem the ingestion pipeline
 * already owns for uploads and duplicating it inside a connector would be the wrong place for
 * it.
 *
 * <p>A skipped file is not a hole in the enumeration. It was never in this adapter's universe,
 * so a crawl that skipped one still enumerated everything it indexes and may still claim
 * completeness. The consequence is deliberate and worth stating: a document converted to a
 * format this adapter does not index stops being mentioned and is retired, which is right,
 * because it stopped being answerable.
 */
final class GoogleDriveDocumentTypes {

    /** Google-native types and what each exports to. */
    private static final Map<String, String> EXPORTS = Map.of(
            "application/vnd.google-apps.document", "text/plain",
            "application/vnd.google-apps.presentation", "text/plain",
            "application/vnd.google-apps.spreadsheet", "text/csv");

    private GoogleDriveDocumentTypes() {
    }

    static boolean isIndexable(String mimeType) {
        return mimeType != null && (EXPORTS.containsKey(mimeType) || isAlreadyText(mimeType));
    }

    /** The type to export to, or null when the file should be downloaded as itself. */
    static String exportTargetFor(String mimeType) {
        return EXPORTS.get(mimeType);
    }

    /** Types stored as themselves that this adapter reads, beyond the {@code text/*} family. */
    private static final Set<String> TEXTUAL = Set.of("application/json", "application/xml");

    private static boolean isAlreadyText(String mimeType) {
        return mimeType.startsWith("text/") || TEXTUAL.contains(mimeType);
    }

    /**
     * The Drive query that excludes what this adapter would skip anyway.
     *
     * <p>Every type {@link #isIndexable} accepts has to appear here. A type this asked Drive not
     * to return can never reach that check, so leaving one out does not filter it — it deletes
     * it, silently and only for files that happen to be of that type.
     */
    static String indexableTypeClause() {
        List<String> terms = new ArrayList<>();
        EXPORTS.keySet().forEach(googleType -> terms.add("mimeType = '" + googleType + "'"));
        TEXTUAL.forEach(textualType -> terms.add("mimeType = '" + textualType + "'"));
        // Drive's query language has no prefix match on mimeType, so the text/* family is let
        // through by substring here and filtered exactly on the way out. Over-fetching metadata
        // is cheap; the expensive call is the content download, and that only happens for what
        // survives that filter.
        terms.add("mimeType contains 'text/'");
        return "(" + String.join(" or ", terms) + ")";
    }
}
