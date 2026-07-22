package com.orgmemory.core.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RelationshipTupleTests {

    @Test
    void acceptsStoredNounRelationships() {
        var tuple = RelationshipTuple.of(
                "user:123",
                "owner",
                "knowledge_asset:456");

        assertEquals("owner", tuple.relation());
    }

    @Test
    void rejectsWritingComputedPermissionsAsFacts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RelationshipTuple.of(
                        "user:123",
                        "can_view",
                        "knowledge_asset:456"));
    }

    @Test
    void rejectsIncompleteOpenFgaReferences() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RelationshipTuple.of(
                        "user:",
                        "owner",
                        "knowledge_asset:456"));
        assertThrows(
                IllegalArgumentException.class,
                () -> RelationshipTuple.of(
                        "user:123",
                        "owner",
                        "knowledge_asset:"));
    }
}
