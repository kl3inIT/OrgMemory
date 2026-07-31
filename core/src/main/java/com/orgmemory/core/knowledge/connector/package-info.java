/**
 * Provider-neutral connector contracts, source profiles, and adapter registries.
 *
 * <p>This nested module remains open while connector crawl and persistence runtime types are
 * moved behind the same boundary and direct sibling dependencies are replaced with intentional
 * APIs.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.connector;
