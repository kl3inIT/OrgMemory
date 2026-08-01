/**
 * Provider-neutral connector contracts, source profiles, and adapter registries.
 *
 * <p>The former reciprocal ACL dependency is now one-way: Connector invokes ACL-owned commands
 * and queries. Source inventory, retirement, and revision phases cross Source Ledger-owned
 * boundaries. The closed module exposes its provider-neutral contracts from this root package
 * and declares every outgoing application-module dependency explicitly.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
        allowedDependencies = {
            "knowledge.acl",
            "knowledge.asset",
            "knowledge.retrieval",
            "knowledge.sourceledger",
            "knowledge.space",
            "knowledge::storage",
            "organization",
            "permission",
            "shared",
            "shared::error",
            "shared::secret"
        })
package com.orgmemory.core.knowledge.connector;
