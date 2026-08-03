package com.orgmemory.core.assetregistry.skill;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.io.InputStream;
import java.util.UUID;

public interface SkillPackageOperations {

    SkillPackageInspection inspectPackage(
            CurrentActor actor, long contentLength, InputStream content);

    UUID importPackage(
            CurrentActor actor,
            String namespace,
            UUID knowledgeSpaceId,
            KnowledgeClassification classification,
            long contentLength,
            InputStream content);

    UUID replacePackage(
            CurrentActor actor,
            UUID assetId,
            long expectedLockVersion,
            long contentLength,
            InputStream content);
}
