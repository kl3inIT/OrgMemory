/**
 * Knowledge Asset aggregate, versioning, publication, catalog, and chunk projection.
 *
 * <p>Graph consumers now resolve immutable asset, version, and chunk facts through an Asset-owned
 * query boundary. Promotion and source publication use Source Ledger-owned contracts rather than
 * its entities or repositories. This nested module remains open while direct Retrieval and
 * external Asset Registry dependencies are replaced with intentional APIs.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.asset;
