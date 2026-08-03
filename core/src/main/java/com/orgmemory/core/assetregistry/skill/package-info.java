/** Agent Skill package semantics, GitHub acquisition, and distribution contracts. */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
        allowedDependencies = {
            "assetregistry::api",
            "assetregistry::consumption",
            "assetregistry::profile",
            "assetregistry::skill-package",
            "assetregistry::skill-delivery",
            "organization",
            "permission",
            "shared::error"
        })
package com.orgmemory.core.assetregistry.skill;
