package com.orgmemory.graphrag.testkit;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FrameworkNeutralityTests {

    private static final String[] SPRING_RUNTIME_MARKERS = {
        "org.springframework.core.SpringVersion",
        "org.springframework.beans.BeanUtils",
        "org.springframework.context.ApplicationContext"
    };

    @Test
    void graphCoreAndTestkitDoNotBringSpringOntoTheRuntimeClasspath() {
        for (String springRuntimeMarker : SPRING_RUNTIME_MARKERS) {
            assertThrows(
                    ClassNotFoundException.class,
                    () -> Class.forName(springRuntimeMarker),
                    springRuntimeMarker + " must not be available");
        }
    }
}
