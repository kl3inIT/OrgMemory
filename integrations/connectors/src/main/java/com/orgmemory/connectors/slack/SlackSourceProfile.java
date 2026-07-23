package com.orgmemory.connectors.slack;

import com.orgmemory.core.knowledge.ConnectorSourceProfile;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;

/**
 * How a Slack crawl maps onto the governed source shape. This used to live in {@code core},
 * which meant the governed ledger knew the name of a source; the adapter declares it now, and
 * {@code core} only collects what adapters declare.
 *
 * <p>Channels arrive as {@code SOURCE_GROUP} principals and members as {@code SOURCE_USER} in
 * the identity payload, so the profile only fixes the OrgMemory sensitivity layer that sits
 * above the source ACL.
 *
 * <p>Content is classified {@code INTERNAL}/{@code ALL_EMPLOYEES}: the OrgMemory classification
 * gate admits any employee so that the resolved channel membership is the binding constraint.
 * Channels needing a stricter OrgMemory classification are a governance concern beyond this
 * adapter.
 */
final class SlackSourceProfile {

    static final String SOURCE_SYSTEM = "slack";

    private SlackSourceProfile() {
    }

    static ConnectorSourceProfile profile() {
        return new ConnectorSourceProfile(
                SOURCE_SYSTEM,
                "Slack",
                KnowledgeClassification.INTERNAL,
                DeclaredAccessScope.ALL_EMPLOYEES,
                "message",
                "text/plain");
    }
}
