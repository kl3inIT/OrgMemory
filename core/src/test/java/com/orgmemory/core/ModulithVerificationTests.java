package com.orgmemory.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithVerificationTests {

    private final ApplicationModules modules = ApplicationModules.of(OrgMemoryModules.class);

    @Test
    void modulesAreWellFormed() {
        modules.verify();
    }

    @Test
    void knowledgeSpaceIsAnOpenNestedModuleDuringTheRefactor() {
        var space = modules.getModuleByName("knowledge.space").orElseThrow();

        assertTrue(space.isOpen());
    }

    @Test
    void sourceLedgerIsAnOpenNestedModuleDuringTheRefactor() {
        var sourceLedger = modules.getModuleByName("knowledge.sourceledger").orElseThrow();

        assertTrue(sourceLedger.isOpen());
    }

    @Test
    void knowledgeAclIsAnOpenNestedModuleDuringTheRefactor() {
        var acl = modules.getModuleByName("knowledge.acl").orElseThrow();

        assertTrue(acl.isOpen());
    }

    @Test
    void knowledgeConnectorIsAnOpenNestedModuleDuringTheRefactor() {
        var connector = modules.getModuleByName("knowledge.connector").orElseThrow();

        assertTrue(connector.isOpen());
    }

    @Test
    void knowledgeAssetIsAnOpenNestedModuleDuringTheRefactor() {
        var asset = modules.getModuleByName("knowledge.asset").orElseThrow();

        assertTrue(asset.isOpen());
    }

    @Test
    void objectStorageIsAnExplicitKnowledgeInterface() {
        var knowledge = modules.getModuleByName("knowledge").orElseThrow();
        var storage = knowledge.getNamedInterfaces().getByName("storage").orElseThrow();

        assertTrue(storage.contains(ObjectStoragePort.class));
    }
}
