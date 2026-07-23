package com.orgmemory.connectors.googledrive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the one relationship between the two halves of this class that nothing else checks: what
 * the crawl asks Drive for, and what it agrees to index, have to describe the same set.
 */
class GoogleDriveDocumentTypesTests {

    /**
     * A type accepted by {@code isIndexable} but absent from the query never arrives, so the
     * filter never runs on it. That reads as filtering and behaves as deletion — the file is
     * simply never mentioned by a crawl that goes on to claim it enumerated everything.
     */
    @Test
    void everyTypeThisIndexesIsAlsoATypeItAsksDriveFor() {
        String clause = GoogleDriveDocumentTypes.indexableTypeClause();

        List<String> indexable = List.of(
                "application/vnd.google-apps.document",
                "application/vnd.google-apps.presentation",
                "application/vnd.google-apps.spreadsheet",
                "application/json",
                "application/xml");
        for (String mimeType : indexable) {
            assertTrue(
                    GoogleDriveDocumentTypes.isIndexable(mimeType),
                    mimeType + " is indexable");
            assertTrue(
                    clause.contains("'" + mimeType + "'"),
                    () -> mimeType + " is indexed but never requested, so it is silently dropped");
        }
        assertTrue(
                clause.contains("mimeType contains 'text/'"),
                "the text family is matched by substring because Drive has no prefix operator");
    }

    @Test
    void skipsWhatTheIngestionPipelineParsesForUploadsRatherThanParsingItHere() {
        assertFalse(GoogleDriveDocumentTypes.isIndexable("application/pdf"));
        assertFalse(GoogleDriveDocumentTypes.isIndexable("image/png"));
        assertFalse(GoogleDriveDocumentTypes.isIndexable(null));
    }

    @Test
    void exportsGoogleOwnFormatsAndDownloadsEverythingElseAsItself() {
        assertEquals(
                "text/plain",
                GoogleDriveDocumentTypes.exportTargetFor("application/vnd.google-apps.document"));
        assertEquals(
                "text/csv",
                GoogleDriveDocumentTypes.exportTargetFor("application/vnd.google-apps.spreadsheet"));
        assertEquals(
                null,
                GoogleDriveDocumentTypes.exportTargetFor("text/markdown"),
                "a file already stored as text has no export, it is downloaded");
    }
}
