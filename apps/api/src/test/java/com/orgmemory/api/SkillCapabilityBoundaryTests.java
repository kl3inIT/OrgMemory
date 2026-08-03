package com.orgmemory.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class SkillCapabilityBoundaryTests {

    private static final Set<String> PARENT_CAPABILITY_PACKAGES = Set.of(
            "com.orgmemory.core.assetregistry.skillpackage.",
            "com.orgmemory.core.assetregistry.skilldelivery.",
            "com.orgmemory.core.assetregistry.skillcleanup.",
            "com.orgmemory.core.assetregistry.skillstorage.");

    @Test
    void apiDoesNotImportParentSkillCapabilities() {
        var dependencies = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.orgmemory.api")
                .stream()
                .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .map(dependency -> dependency.getTargetClass().getName())
                .filter(name -> PARENT_CAPABILITY_PACKAGES.stream()
                        .anyMatch(name::startsWith))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(Set.of(), dependencies);
    }

    @Test
    void apiImportsOnlyTheSkillApplicationSurface() {
        var dependencies = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.orgmemory.api")
                .stream()
                .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .map(dependency -> dependency.getTargetClass().getName())
                .filter(name -> name.startsWith(
                        "com.orgmemory.core.assetregistry.skill."))
                .map(name -> name.replaceFirst("\\$.*$", ""))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.assetregistry.skill.SkillDistributionOperations",
                        "com.orgmemory.core.assetregistry.skill.SkillGitHubOperations",
                        "com.orgmemory.core.assetregistry.skill.SkillGitHubSourcePort",
                        "com.orgmemory.core.assetregistry.skill.SkillInstallManifest",
                        "com.orgmemory.core.assetregistry.skill.SkillPackageContent",
                        "com.orgmemory.core.assetregistry.skill.SkillPackageInspection",
                        "com.orgmemory.core.assetregistry.skill.SkillPackageOperations"),
                dependencies);
    }
}
