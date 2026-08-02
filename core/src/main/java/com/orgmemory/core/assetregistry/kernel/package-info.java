/**
 * Canonical Asset identity, accountable-role, authorization-intent, and readiness ledger.
 *
 * <p>Parent-facing commands and immutable values belong to {@code assetregistry::api}. The
 * Kernel exposes only its opaque projection queue to the sibling authorization module.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
        allowedDependencies = {"assetregistry::api", "authorization", "shared"})
package com.orgmemory.core.assetregistry.kernel;
