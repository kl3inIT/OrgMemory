package com.orgmemory.core.knowledge.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ObjectKeyTests {

    @Test
    void normalizesDirectorySeparators() {
        assertEquals("organizations/source/file.txt", new ObjectKey("organizations\\source\\file.txt").value());
    }

    @Test
    void rejectsParentDirectorySegmentsAnywhereInTheKey() {
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey(".."));
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey("documents/../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey("documents\\..\\secret.txt"));
    }
}
