/**
 * Knowledge Asset aggregate, versioning, publication, catalog, and chunk projection.
 *
 * <p>Graph consumers now resolve immutable asset, version, and chunk facts through an Asset-owned
 * query boundary. Promotion and source publication use Source Ledger-owned contracts rather than
 * its entities or repositories. Catalog projections, normalized text chunks, and pgvector
 * encoding are Asset-owned persistence-facing values consumed by Retrieval. External catalog
 * consumers cross the parent {@code knowledge::catalog} interface rather than this nested module.
 * The compact embedding profile reference required for publication and projection namespace
 * identity are also owned here; callers translate richer Retrieval profiles at the boundary.
 * Asset has no direct dependency on Retrieval. The closed module exposes its owner-defined
 * contracts from this root package and declares every outgoing application-module dependency
 * explicitly.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
        allowedDependencies = {
            "authorization",
            "knowledge.sourceledger",
            "organization",
            "permission",
            "shared",
            "shared::error"
        })
package com.orgmemory.core.knowledge.asset;
