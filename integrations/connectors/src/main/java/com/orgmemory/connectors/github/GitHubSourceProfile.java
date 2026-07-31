package com.orgmemory.connectors.github;

import com.orgmemory.core.knowledge.connector.ConnectorSourceProfile;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;

/** Governance defaults for private GitHub issue and pull-request descriptions. */
final class GitHubSourceProfile {

    static final String SOURCE_SYSTEM = "github";

    private GitHubSourceProfile() {
    }

    static ConnectorSourceProfile profile() {
        return new ConnectorSourceProfile(
                SOURCE_SYSTEM,
                "GitHub",
                KnowledgeClassification.INTERNAL,
                DeclaredAccessScope.ALL_EMPLOYEES,
                "work_item",
                "text/plain");
    }
}

