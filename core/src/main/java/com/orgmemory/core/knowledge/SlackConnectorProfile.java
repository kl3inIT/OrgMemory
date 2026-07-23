package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;

/**
 * The staging profile for the Slack source system: how a Slack crawl maps to the governed
 * source shape. Channels arrive as {@code SOURCE_GROUP} principals and members as
 * {@code SOURCE_USER} in the identity payload, so the profile only fixes the source type and
 * the OrgMemory sensitivity layer that sits above the source ACL.
 *
 * <p>Content is classified {@code INTERNAL}/{@code ALL_EMPLOYEES}: the OrgMemory
 * classification gate admits any employee so the resolved channel membership is the binding
 * constraint. Channels needing a stricter OrgMemory classification are a governance concern
 * beyond this staging slice.
 */
final class SlackConnectorProfile {

    static final String SOURCE_SYSTEM = "slack";
    static final SourceType SOURCE_TYPE = SourceType.SLACK;
    static final KnowledgeClassification CLASSIFICATION = KnowledgeClassification.INTERNAL;
    static final DeclaredAccessScope DECLARED_ACCESS = DeclaredAccessScope.ALL_EMPLOYEES;
    static final String OBJECT_TYPE = "message";
    static final String MEDIA_TYPE = "text/plain";

    private SlackConnectorProfile() {
    }

    static boolean supports(String sourceSystem) {
        return sourceSystem != null && SOURCE_SYSTEM.equalsIgnoreCase(sourceSystem.trim());
    }
}
