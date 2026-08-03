package com.orgmemory.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class SkillCapabilityBoundaryTests {

    @Test
    void workerImportsOnlyTheCleanupCapability() {
        var dependencies = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.orgmemory.worker")
                .stream()
                .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .map(dependency -> dependency.getTargetClass().getName())
                .filter(name -> name.startsWith(
                        "com.orgmemory.core.assetregistry.skill"))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.assetregistry.skillcleanup.SkillPackageCleanupOperations",
                        "com.orgmemory.core.assetregistry.skillcleanup.SkillPackageCleanupSummary"),
                dependencies);
    }
}
