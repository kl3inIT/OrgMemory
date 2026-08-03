package com.orgmemory.core.assetregistry.skill;

import static com.orgmemory.core.assetregistry.AssetProfileValidationTests.skillPayload;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SkillPackageProfileTests {

    private final SkillPackageProfile skills = new SkillPackageProfile();

    @Test
    void rejectsInvalidPackageDigests() {
        assertThrows(
                IllegalArgumentException.class,
                () -> skills.validate(
                        skillPayload().replace(
                                "\"sha256\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"",
                                "\"sha256\": \"not-a-sha256\"")));
    }

    @Test
    void readsLegacyAndCurrentPayloadsWithoutOrigin() {
        SkillPackageSpec legacy = skills.read(skillPayload());
        SkillPackageSpec current = skills.read(skillPayload().replace(
                "\"artifact\": {", "\"origin\": null, \"artifact\": {"));

        assertNull(legacy.origin());
        assertNull(current.origin());
        skills.validate(skillPayload());
    }
}
