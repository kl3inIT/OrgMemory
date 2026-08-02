package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.organization.CurrentActor;
import java.util.UUID;

/** Opens one current source through canonical authorization without exposing storage keys. */
public interface SourceContentService {

    SourceContent open(CurrentActor actor, UUID sourceId, String requestId);
}
