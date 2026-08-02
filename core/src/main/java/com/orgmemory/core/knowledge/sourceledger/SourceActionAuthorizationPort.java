package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.organization.CurrentActor;
import java.util.Set;
import java.util.UUID;

/** Resolves source actions from the authorization system without granting authority itself. */
public interface SourceActionAuthorizationPort {

    Set<UUID> deletableKnowledgeAssetIds(CurrentActor actor);
}
