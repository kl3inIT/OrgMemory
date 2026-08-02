/** Deterministic Prompt Template rendering, execution, and bounded evaluation. */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
        allowedDependencies = {
            "assetregistry::profile",
            "assetregistry::consumption",
            "assetregistry::api",
            "assetregistry::prompt",
            "ai",
            "knowledge::search",
            "organization",
            "shared",
            "shared::error"
        })
package com.orgmemory.core.assetregistry.prompt;
