/**
 * Knowledge graph indexing, processing profiles, exploration, curation, and export.
 *
 * <p>Asset, Source Ledger, ACL, Space, and embedding-profile state now crosses owned query or
 * registry boundaries instead of persistence types. This nested module remains open only until
 * its exact outgoing dependency allowlist is verified in the module-closing cycle.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.graph;
