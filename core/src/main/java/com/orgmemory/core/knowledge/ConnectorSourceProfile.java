package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;

/**
 * How one source system's crawl maps onto the governed source shape.
 *
 * <p>A profile is the whole of what {@code core} needs to know about a source, and adapters
 * contribute their own rather than {@code core} holding a list. The adapter owns the wire
 * shape — HTTP, pagination, rate limits, authentication — and this owns the governance shape:
 * what one crawled object is, and what OrgMemory sensitivity layer sits above the source's own
 * ACL. Nothing else about a source belongs here.
 *
 * @param sourceSystem   the stable lower-case name a crawl batch identifies itself by
 * @param displayName    what an administrator calls it
 * @param classification the OrgMemory sensitivity layer above the source ACL
 * @param declaredAccess the declared scope that pairs with the classification
 * @param objectType     what one crawled object is, recorded on the raw staging record
 * @param mediaType      the media type crawled content is stored as
 */
public record ConnectorSourceProfile(
        String sourceSystem,
        String displayName,
        KnowledgeClassification classification,
        DeclaredAccessScope declaredAccess,
        String objectType,
        String mediaType) {

    public ConnectorSourceProfile {
        sourceSystem = requireText(sourceSystem, "sourceSystem").toLowerCase();
        displayName = requireText(displayName, "displayName");
        objectType = requireText(objectType, "objectType");
        mediaType = requireText(mediaType, "mediaType");
        if (classification == null || declaredAccess == null) {
            throw new IllegalArgumentException(
                    "A source profile needs a classification and a declared access scope");
        }
    }

    /**
     * Everything a connector crawls is governed by its source. A profile does not get to
     * choose otherwise: a source OrgMemory were the record for would not be a connector, and
     * letting an adapter claim authority over data it merely reads is the whole failure this
     * separation exists to prevent.
     */
    public AclAuthority aclAuthority() {
        return AclAuthority.SOURCE;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A source profile needs a " + field);
        }
        return value.trim();
    }
}
