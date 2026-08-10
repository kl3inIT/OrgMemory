package com.orgmemory.core.assetregistry.skill;

import com.orgmemory.core.organization.CurrentActor;
import java.util.UUID;

/** Actor-scoped Skill enablement contract for API and runtime consumers. */
public interface SkillActivationOperations {

    boolean isEnabled(CurrentActor actor, UUID assetId);

    boolean setEnabled(CurrentActor actor, UUID assetId, boolean enabled);
}
