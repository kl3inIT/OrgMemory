package com.orgmemory.connectors.googledrive;

import com.orgmemory.core.knowledge.acl.SourcePrincipalKind;

import com.orgmemory.core.knowledge.connector.ConnectorAclGrant;
import com.orgmemory.core.permission.AccessGate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Turns one file's Drive sharing into the grants the ledger seals. The permissions may have
 * arrived inline with the listing or through {@code permissions.list} — Drive uses the same
 * shape for both, and which road they took is not this mapper's concern.
 *
 * <p>A connector may only translate what the source states, never widen it, and the two
 * decisions here are both about refusing to widen.
 *
 * <p><b>A domain permission becomes a group.</b> Drive says "everyone at example.com may read
 * this" without enumerating the users. The grant is therefore keyed by Drive's stable permission
 * id and its membership is captured separately as incomplete until an authoritative Directory
 * integration can enumerate it.
 *
 * <p><b>An {@code anyone} permission grants nothing.</b> A public link is a statement about
 * people outside the organization; translating it into an internal grant would widen access on
 * the strength of a setting that says nothing about who inside may read.
 */
final class GoogleDrivePermissionMapper {

    private GoogleDrivePermissionMapper() {
    }

    static List<ConnectorAclGrant> grantsFor(Iterable<JsonNode> permissions) {
        List<ConnectorAclGrant> grants = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode permission : permissions) {
            if (permission.path("deleted").asBoolean(false)) {
                continue;
            }
            String nativeId = principalNativeIdOf(permission);
            if (nativeId == null || !seen.add(kindOf(permission) + ":" + nativeId)) {
                continue;
            }
            grants.add(new ConnectorAclGrant(kindOf(permission), nativeId, AccessGate.ALLOW));
        }
        return grants;
    }

    static SourcePrincipalKind kindOf(JsonNode permission) {
        return "user".equals(permission.path("type").asString(""))
                ? SourcePrincipalKind.SOURCE_USER
                : SourcePrincipalKind.SOURCE_GROUP;
    }

    /** The stable Drive-owned id a grant names, or null when it grants nothing here. */
    static String principalNativeIdOf(JsonNode permission) {
        String type = permission.path("type").asString("");
        return switch (type) {
            case "user", "group", "domain" -> {
                String permissionId = permission.path("id").asString("").strip();
                yield permissionId.isEmpty() ? null : permissionId;
            }
            // "anyone", and anything Drive adds later. A permission type this adapter does not
            // understand grants nothing rather than being guessed at.
            default -> null;
        };
    }
}
