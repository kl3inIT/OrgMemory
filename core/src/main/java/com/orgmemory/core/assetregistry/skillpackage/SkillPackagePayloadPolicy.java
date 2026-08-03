package com.orgmemory.core.assetregistry.skillpackage;

public interface SkillPackagePayloadPolicy {

    void validate(String canonicalPayload, SkillPackageArtifact artifact);

    SkillPackageArtifact artifact(String canonicalPayload);
}
