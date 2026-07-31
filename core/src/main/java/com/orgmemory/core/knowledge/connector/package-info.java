/**
 * Provider-neutral connector contracts, source profiles, and adapter registries.
 *
 * <p>The former reciprocal ACL dependency is now one-way: Connector invokes ACL-owned commands
 * and queries. This module remains open while direct source-ledger, Asset, and Graph dependencies
 * are replaced with intentional APIs.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.connector;
