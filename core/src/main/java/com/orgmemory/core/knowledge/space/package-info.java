/**
 * Knowledge Space lifecycle, administration, and authorized target lookup.
 *
 * <p>Sibling consumers resolve Space existence and activity through an owned query boundary
 * instead of accessing Space persistence directly. The closed boundary exposes only root-package
 * contracts and limits outgoing dependencies to authorization, Source Ledger, organization,
 * permission audit, and shared foundations.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
        allowedDependencies = {
            "authorization",
            "knowledge.sourceledger",
            "organization",
            "permission",
            "shared",
            "shared::error"
        })
package com.orgmemory.core.knowledge.space;
