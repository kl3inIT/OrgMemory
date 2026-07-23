package com.orgmemory.graphrag.testkit;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FrameworkNeutralityTests {

    @Test
    void graphCoreAndTestkitDoNotBringSpringOntoTheRuntimeClasspath() {
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName("org.springframework.context.ApplicationContext"));
    }
}
