/**
 * Provider-neutral connector contracts, source profiles, and adapter registries.
 *
 * <p>This nested module remains open while direct ACL, source-ledger, Asset, and Graph dependencies
 * are replaced with intentional APIs.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.connector;
