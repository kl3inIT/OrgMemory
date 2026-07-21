package com.orgmemory.worker.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PermissionDatasetValidationCliTests {

    @TempDir
    Path tempDirectory;

    @Test
    void returnsValidationExitCodeAndActionableReport() throws Exception {
        Path workbook = PermissionWorkbookFixture.write(
                tempDirectory.resolve("mismatch.xlsx"), true, "Deny");
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();

        int exitCode = PermissionDatasetValidationCli.run(
                new String[] {workbook.toString()},
                new PrintStream(outputBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errorBytes, true, StandardCharsets.UTF_8));

        String output = outputBytes.toString(StandardCharsets.UTF_8);
        assertEquals(1, exitCode);
        assertTrue(errorBytes.toString(StandardCharsets.UTF_8).isBlank());
        assertTrue(output.contains("evaluations=2 evaluated=2 matched=1 errors=1"));
        assertTrue(output.contains("EVALUATION_EXPECTATION_MISMATCH"));
    }

    @Test
    void invalidPathUsesFormatFailureExitCode() {
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();

        int exitCode = PermissionDatasetValidationCli.run(
                new String[] {"\0"},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(errorBytes, true, StandardCharsets.UTF_8));

        assertEquals(2, exitCode);
        assertTrue(errorBytes.toString(StandardCharsets.UTF_8).contains("Unable to validate permission dataset"));
    }
}
