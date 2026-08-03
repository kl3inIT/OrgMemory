/** Work Instruction schema, acknowledgement, and relation semantics. */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
        allowedDependencies = {
            "assetregistry::api",
            "assetregistry::consumption",
            "assetregistry::profile",
            "assetregistry::work-instruction",
            "assetregistry::work-instruction-relations",
            "knowledge::catalog",
            "organization",
            "shared"
        })
package com.orgmemory.core.assetregistry.workinstruction;
