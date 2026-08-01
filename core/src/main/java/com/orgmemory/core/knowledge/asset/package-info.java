/**
 * Knowledge Asset aggregate, versioning, publication, catalog, and chunk projection.
 *
 * <p>Graph consumers now resolve immutable asset, version, and chunk facts through an Asset-owned
 * query boundary. Promotion and source publication use Source Ledger-owned contracts rather than
 * its entities or repositories. Catalog projections, normalized text chunks, and pgvector
 * encoding are Asset-owned values consumed by Retrieval and Asset Registry. The compact embedding
 * profile reference required for publication and projection namespace identity are also owned
 * here; callers translate richer Retrieval profiles at the boundary. Asset has no direct
 * dependency on Retrieval and remains open only while its incoming consumer surface is closed.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.asset;
