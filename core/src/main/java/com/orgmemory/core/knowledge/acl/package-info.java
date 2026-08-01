/**
 * Source ACL evidence, external principals, mappings, and sealed group memberships.
 *
 * <p>Connector and Source Ledger now consume ACL-owned inputs, so this module no longer
 * depends on either implementation. Its source-ingestion facade owns ACL validation,
 * snapshot/head persistence, sealing, and readiness queries without exposing JPA types. The
 * Retrieval and Graph consume immutable facts through ACL-owned facades rather than repositories
 * or persistence entities. The closed boundary exposes only types in this root package and limits
 * outgoing dependencies to organization, permission, and shared foundations.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
        allowedDependencies = {
            "organization",
            "permission",
            "shared",
            "shared::error"
        })
package com.orgmemory.core.knowledge.acl;
