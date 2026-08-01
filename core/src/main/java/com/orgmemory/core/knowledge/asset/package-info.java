/**
 * Knowledge Asset aggregate, versioning, publication, catalog, and chunk projection.
 *
 * <p>Graph consumers now resolve immutable asset, version, and chunk facts through an Asset-owned
 * query boundary. Promotion and source publication use Source Ledger-owned contracts rather than
 * its entities or repositories. Catalog projections, normalized text chunks, and pgvector
 * encoding are Asset-owned values consumed by Retrieval and Asset Registry. This nested module
 * remains open while its remaining direct Retrieval behavior is replaced with intentional APIs.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.asset;
