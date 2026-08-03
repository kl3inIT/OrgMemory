package com.orgmemory.core.assetregistry.skillpackage;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.UUID;

public interface SkillPackageAssetCommand {

    void requireCreate(CurrentActor actor, UUID knowledgeSpaceId);

    KnowledgeClassification requireEdit(CurrentActor actor, UUID assetId);

    UUID importPackage(
            CurrentActor actor,
            String namespace,
            UUID knowledgeSpaceId,
            KnowledgeClassification classification,
            SkillPackageUpload upload);

    UUID replacePackage(
            CurrentActor actor,
            UUID assetId,
            long expectedLockVersion,
            SkillPackageUpload upload);
}
