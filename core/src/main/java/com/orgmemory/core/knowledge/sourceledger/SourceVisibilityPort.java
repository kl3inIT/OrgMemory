package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.organization.CurrentActor;
import java.util.List;
import java.util.UUID;

/** Resolves source objects visible through governed retrieval authorization. */
public interface SourceVisibilityPort {

    List<UUID> visibleSourceObjectIds(CurrentActor actor);
}
