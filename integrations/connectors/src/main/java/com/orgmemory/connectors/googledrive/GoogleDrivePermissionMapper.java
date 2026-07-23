package com.orgmemory.connectors.googledrive;

import com.orgmemory.core.knowledge.ConnectorAclGrant;
import com.orgmemory.core.knowledge.SourcePrincipalKind;
import com.orgmemory.core.permission.AccessGate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Turns one file's Drive sharing into the grants the ledger seals.
 *
 * <p>A connector may only translate what the source states, never widen it, and the two
 * decisions here are both about refusing to widen.
 *
 * <p><b>A domain permission becomes a group.</b> Drive says "everyone at example.com may read
 * this" without naming them, so the grant is to a group keyed on the domain whose membership is
 * every user this crawl observed at that domain. The Drive API cannot enumerate a domain's
 * users — that is the Admin SDK — so this under-grants: a document shared company-wide is
 * retrievable by the employees the crawl has seen holding a permission somewhere, not by those
 * it has not. Under-granting is the direction a permission-aware system is allowed to be wrong
 * in, and it resolves itself as more of the Drive is crawled.
 *
 * <p><b>An {@code anyone} permission grants nothing.</b> A public link is a statement about
 * people outside the organization; translating it into an internal grant would widen access on
 * the strength of a setting that says nothing about who inside may read.
 */
final class GoogleDrivePermissionMapper {

    /** Prefix so a domain group can never collide with a real Google group's address. */
    static final String DOMAIN_GROUP_PREFIX = "domain:";

    private GoogleDrivePermissionMapper() {
    }

    static List<ConnectorAclGrant> grantsFor(JsonNode file) {
        List<ConnectorAclGrant> grants = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode permission : file.path("permissions")) {
            if (permission.path("deleted").asBoolean(false)) {
                continue;
            }
            String key = principalKeyOf(permission);
            if (key == null || !seen.add(key)) {
                continue;
            }
            grants.add(new ConnectorAclGrant(kindOf(permission), key, AccessGate.ALLOW));
        }
        return grants;
    }

    /** Every domain a file's permissions grant to, so the crawl knows which groups to declare. */
    static Set<String> domainsGrantedBy(JsonNode file) {
        Set<String> domains = new LinkedHashSet<>();
        for (JsonNode permission : file.path("permissions")) {
            if (!"domain".equals(permission.path("type").asString(""))) {
                continue;
            }
            String domain = permission.path("domain").asString("").strip().toLowerCase();
            if (!domain.isEmpty()) {
                domains.add(domain);
            }
        }
        return domains;
    }

    static String domainGroupKey(String domain) {
        return DOMAIN_GROUP_PREFIX + domain.strip().toLowerCase();
    }

    private static SourcePrincipalKind kindOf(JsonNode permission) {
        return "user".equals(permission.path("type").asString(""))
                ? SourcePrincipalKind.SOURCE_USER
                : SourcePrincipalKind.SOURCE_GROUP;
    }

    /** The external key a grant names, or null when this permission grants nothing here. */
    private static String principalKeyOf(JsonNode permission) {
        String type = permission.path("type").asString("");
        String email = permission.path("emailAddress").asString("").strip().toLowerCase();
        return switch (type) {
            case "user", "group" -> email.isEmpty() ? null : email;
            case "domain" -> {
                String domain = permission.path("domain").asString("").strip().toLowerCase();
                yield domain.isEmpty() ? null : domainGroupKey(domain);
            }
            // "anyone", and anything Drive adds later. A permission type this adapter does not
            // understand grants nothing rather than being guessed at.
            default -> null;
        };
    }
}
