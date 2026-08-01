/**
 * Provider-neutral connector contracts, source profiles, and adapter registries.
 *
 * <p>The former reciprocal ACL dependency is now one-way: Connector invokes ACL-owned commands
 * and queries. Source inventory, retirement, and revision phases now cross Source Ledger-owned
 * boundaries; this module remains open while direct Asset and Retrieval translations are
 * replaced with intentional APIs.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.connector;
