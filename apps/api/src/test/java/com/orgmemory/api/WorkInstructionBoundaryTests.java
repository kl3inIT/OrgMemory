package com.orgmemory.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class WorkInstructionBoundaryTests {

    @Test
    void apiImportsOnlyTheWorkInstructionOperationsContract() {
        var dependencies = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.orgmemory.api")
                .stream()
                .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .map(dependency -> dependency.getTargetClass().getName())
                .filter(name -> name.startsWith(
                        "com.orgmemory.core.assetregistry.workinstruction"))
                .map(name -> name.replaceFirst("\\$.*$", ""))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.assetregistry.workinstructioncontract.WorkInstructionOperations",
                        "com.orgmemory.core.assetregistry.workinstructioncontract.WorkInstructionView"),
                dependencies);
    }

    @Test
    void operationsContractHasOnlyItsTwoApiConsumers() {
        var consumers = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.orgmemory.api")
                .stream()
                .filter(type -> type.getDirectDependenciesFromSelf().stream()
                        .anyMatch(dependency -> dependency.getTargetClass()
                                .getPackageName()
                                .equals("com.orgmemory.core.assetregistry.workinstructioncontract")))
                .map(type -> type.getName().replaceFirst("\\$.*$", ""))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.api.assetregistry.AssetConsumptionController",
                        "com.orgmemory.api.assistant.AssistantConfiguration"),
                consumers);
    }
}
