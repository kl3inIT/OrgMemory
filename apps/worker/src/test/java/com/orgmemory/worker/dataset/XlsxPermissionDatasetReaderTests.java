package com.orgmemory.worker.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.permission.dataset.PermissionDataset;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XlsxPermissionDatasetReaderTests {

    @TempDir
    Path tempDirectory;

    @Test
    void readsWorkbookNormalizesDepartmentAliasesAndSplitsDocumentLists() throws Exception {
        Path workbook = PermissionWorkbookFixture.write(
                tempDirectory.resolve("permission-dataset.xlsx"), true, "Allow");

        PermissionDataset dataset = new XlsxPermissionDatasetReader().read(workbook);

        assertEquals("HR", dataset.subjects().getFirst().departmentId());
        assertEquals("HR", dataset.resources().get(1).departmentId());
        assertEquals(2, dataset.evaluations().get(1).resourceIds().size());
        assertEquals("DOC011", dataset.evaluations().get(1).resourceIds().get(1));
    }

    @Test
    void rejectsWorkbookWithMissingRequiredSheet() throws Exception {
        Path workbook = PermissionWorkbookFixture.write(
                tempDirectory.resolve("missing-permissions.xlsx"), false, "Allow");

        PermissionDatasetFormatException exception = assertThrows(
                PermissionDatasetFormatException.class,
                () -> new XlsxPermissionDatasetReader().read(workbook));

        assertTrue(exception.getMessage().contains("Missing required sheet: Permissions"));
    }

    @Test
    void rejectsPartiallyPopulatedRowWithBlankIdentifier() throws Exception {
        Path workbook = PermissionWorkbookFixture.writeWithBlankEvaluationId(
                tempDirectory.resolve("blank-evaluation-id.xlsx"));

        PermissionDatasetFormatException exception = assertThrows(
                PermissionDatasetFormatException.class,
                () -> new XlsxPermissionDatasetReader().read(workbook));

        assertTrue(exception.getMessage().contains("column question_id: Required value is blank"));
    }
}
