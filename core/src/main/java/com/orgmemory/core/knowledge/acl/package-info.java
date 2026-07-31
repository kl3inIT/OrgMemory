/**
 * Source ACL evidence, external principals, mappings, and sealed group memberships.
 *
 * <p>Connector and Source Ledger now consume ACL-owned inputs, so this module no longer
 * depends on either implementation. It remains open while its remaining retrieval seam is
 * replaced with an explicit API.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.acl;
