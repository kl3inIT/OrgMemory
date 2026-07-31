package com.orgmemory.core.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PgVectorLiteralTests {

    @Test
    void parsesValidVectorLiteral() {
        var vector = PgVectorLiteral.parse("[1.0, -2.5,3]");

        assertArrayEquals(new float[] {1.0F, -2.5F, 3.0F}, vector.copyValues());
    }

    @Test
    void rejectsEmptyVectorComponents() {
        assertThrows(IllegalArgumentException.class, () -> PgVectorLiteral.parse("[1,]"));
        assertThrows(IllegalArgumentException.class, () -> PgVectorLiteral.parse("[,1]"));
        assertThrows(IllegalArgumentException.class, () -> PgVectorLiteral.parse("[1,,2]"));
        assertThrows(IllegalArgumentException.class, () -> PgVectorLiteral.parse("[1, ,2]"));
    }
}
