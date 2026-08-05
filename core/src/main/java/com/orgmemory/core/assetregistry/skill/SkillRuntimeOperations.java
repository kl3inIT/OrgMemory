package com.orgmemory.core.assetregistry.skill;

import com.orgmemory.core.organization.CurrentActor;
import java.util.List;
import java.util.UUID;

/** Read-only progressive-disclosure view of governed Skill releases. */
public interface SkillRuntimeOperations {

    List<SkillSummary> search(CurrentActor actor, String query, int limit);

    ActivatedSkill activate(CurrentActor actor, UUID assetId, UUID releaseId);

    SkillResource readResource(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId,
            String path);

    record SkillSummary(
            UUID assetId,
            UUID releaseId,
            String coordinate,
            String version,
            String title,
            String description,
            String releaseDigest) {
    }

    record ActivatedSkill(
            SkillSummary skill,
            String instructions,
            List<String> resources) {

        public ActivatedSkill {
            resources = List.copyOf(resources);
        }
    }

    record SkillResource(
            SkillSummary skill,
            String path,
            String content) {
    }
}
