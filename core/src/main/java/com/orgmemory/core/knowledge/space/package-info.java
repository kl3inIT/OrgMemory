/**
 * Knowledge Space lifecycle, administration, and authorized target lookup.
 *
 * <p>Sibling consumers resolve Space existence and activity through an owned query boundary
 * instead of accessing Space persistence directly. The module remains open until its public
 * contracts and outgoing dependency allowlist are mechanically verified for closure.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.orgmemory.core.knowledge.space;
