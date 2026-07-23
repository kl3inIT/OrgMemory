package com.orgmemory.connectors.googledrive;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The half of a connection's settings only Drive understands, read out of the opaque document
 * the ledger stored for it.
 *
 * <p>Parsing here rather than in {@code core} is the point of the document being opaque. The
 * ledger has no basis for an opinion about what a folder id is; this adapter defined the shape
 * and is the only thing that should read it.
 *
 * <p>A missing or unreadable document is not a failure, for the same reason as Slack's: a
 * connection somebody enabled without filling in the optional half should crawl what the account
 * can see with the default bound, not refuse to run.
 *
 * @param folderIds           folders to crawl, each including everything beneath it; empty means
 *                            everything the account can see
 * @param impersonatedUser    who to read as, with domain-wide delegation; empty means the
 *                            service account acts as itself
 * @param includeSharedDrives whether shared drives are in scope as well as shared files
 * @param maxFiles            a bound on one crawl so a large Drive cannot run unbounded
 * @param maxFileBytes        the largest file whose text this will read
 */
record GoogleDriveCrawlSettings(
        List<String> folderIds,
        String impersonatedUser,
        boolean includeSharedDrives,
        int maxFiles,
        long maxFileBytes) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_MAX_FILES = 500;
    /** Ten mebibytes of text is far past what a useful answer is chunked out of. */
    private static final long DEFAULT_MAX_FILE_BYTES = 10L * 1024 * 1024;

    GoogleDriveCrawlSettings {
        folderIds = folderIds == null ? List.of() : List.copyOf(folderIds);
        impersonatedUser = impersonatedUser == null ? "" : impersonatedUser.strip();
        maxFiles = maxFiles <= 0 ? DEFAULT_MAX_FILES : maxFiles;
        maxFileBytes = maxFileBytes <= 0 ? DEFAULT_MAX_FILE_BYTES : maxFileBytes;
    }

    static GoogleDriveCrawlSettings from(String sourceConfig) {
        if (sourceConfig == null || sourceConfig.isBlank()) {
            return defaults();
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(sourceConfig);
        } catch (RuntimeException unreadable) {
            return defaults();
        }
        if (!root.isObject()) {
            return defaults();
        }
        List<String> folderIds = new ArrayList<>();
        JsonNode configured = root.path("folderIds");
        if (configured.isArray()) {
            for (JsonNode folder : configured) {
                String id = folder.asString("").strip();
                if (!id.isEmpty()) {
                    folderIds.add(id);
                }
            }
        }
        return new GoogleDriveCrawlSettings(
                folderIds,
                root.path("impersonatedUser").asString(""),
                // Shared drives are in scope unless somebody said otherwise: a crawl that
                // silently skipped them would look like an emptied Drive to the reconciler.
                root.path("includeSharedDrives").asBoolean(true),
                root.path("maxFiles").asInt(0),
                root.path("maxFileBytes").asLong(0));
    }

    /** A folder filter means this crawl did not enumerate the connection, and cannot claim to. */
    boolean enumeratesEverything() {
        return folderIds.isEmpty();
    }

    private static GoogleDriveCrawlSettings defaults() {
        return new GoogleDriveCrawlSettings(
                List.of(), "", true, DEFAULT_MAX_FILES, DEFAULT_MAX_FILE_BYTES);
    }
}
