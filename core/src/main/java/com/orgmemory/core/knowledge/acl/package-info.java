/**
 * Source ACL evidence, external principals, mappings, and sealed group memberships.
 *
 * <p>This nested module remains open while connector-owned crawl inputs and connection lookup are
 * replaced with intentional contracts that remove the ACL/Connector cycle.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.acl;
