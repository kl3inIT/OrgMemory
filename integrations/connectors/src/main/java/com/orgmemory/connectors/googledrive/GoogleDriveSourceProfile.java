package com.orgmemory.connectors.googledrive;

import com.orgmemory.core.knowledge.ConnectorSourceProfile;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;

/**
 * How a Drive crawl maps onto the governed source shape.
 *
 * <p>The whole file is the second source's declaration of itself, and it is the measure of the
 * last increment: nothing in {@code core} had to learn the name {@code google_drive} for this to
 * be governed.
 *
 * <p>Content is classified {@code INTERNAL}/{@code ALL_EMPLOYEES} for the same reason Slack's is:
 * the OrgMemory classification gate admits any employee so that the file's own Drive sharing is
 * the binding constraint. A document needing a stricter OrgMemory classification is a governance
 * concern beyond this adapter.
 */
final class GoogleDriveSourceProfile {

    static final String SOURCE_SYSTEM = "google_drive";

    private GoogleDriveSourceProfile() {
    }

    static ConnectorSourceProfile profile() {
        return new ConnectorSourceProfile(
                SOURCE_SYSTEM,
                "Google Drive",
                KnowledgeClassification.INTERNAL,
                DeclaredAccessScope.ALL_EMPLOYEES,
                "document",
                "text/plain");
    }
}
