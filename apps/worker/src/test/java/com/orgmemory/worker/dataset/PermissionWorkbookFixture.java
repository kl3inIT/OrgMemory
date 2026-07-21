package com.orgmemory.worker.dataset;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class PermissionWorkbookFixture {

    private PermissionWorkbookFixture() {
    }

    static Path write(Path path, boolean includePermissions, String secondExpectedPermission) throws IOException {
        return write(path, includePermissions, secondExpectedPermission, false);
    }

    static Path writeWithBlankEvaluationId(Path path) throws IOException {
        return write(path, true, "Allow", true);
    }

    private static Path write(
            Path path,
            boolean includePermissions,
            String secondExpectedPermission,
            boolean includeBlankEvaluationId) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            table(
                    workbook,
                    "Documents",
                    List.of("document_id"),
                    List.of(List.of("DOC001"), List.of("DOC011")));
            table(
                    workbook,
                    "Document_Metadata",
                    List.of("document_id", "department", "classification", "allowed_access"),
                    List.of(
                            List.of("DOC001", "Company", "Public", "All"),
                            List.of("DOC011", "HR", "Confidential", "Own Department")));
            table(
                    workbook,
                    "Users",
                    List.of("user_id", "department", "role", "status"),
                    List.of(List.of("U001", "Human Resources", "Employee", "Active")));
            table(
                    workbook,
                    "Departments",
                    List.of("department_id", "department_en"),
                    List.of(List.of("COMP", "Company"), List.of("HR", "Human Resources")));
            table(
                    workbook,
                    "Roles",
                    List.of("role_en"),
                    List.of(
                            List.of("Employee"),
                            List.of("Manager"),
                            List.of("Director"),
                            List.of("Executive")));
            if (includePermissions) {
                table(
                        workbook,
                        "Permissions",
                        List.of("classification", "employee", "manager", "director", "executive"),
                        List.of(
                                List.of("Public", "Allow", "Allow", "Allow", "Allow"),
                                List.of("Internal", "Allow", "Allow", "Allow", "Allow"),
                                List.of(
                                        "Confidential",
                                        "Own Department",
                                        "Own Department",
                                        "Own Department",
                                        "Allow"),
                                List.of("Restricted", "Deny", "Deny", "Deny", "Allow")));
            }
            List<List<String>> evaluationRows = new ArrayList<>(List.of(
                    List.of("P001", "U001", "Allow", "DOC001"),
                    List.of("P002", "U001", secondExpectedPermission, "DOC001; DOC011")));
            if (includeBlankEvaluationId) {
                evaluationRows.add(List.of("", "U001", "Allow", "DOC001"));
            }
            table(
                    workbook,
                    "Public_Evaluation",
                    List.of("question_id", "user_id", "expected_permission", "expected_document_id"),
                    evaluationRows);

            try (OutputStream output = Files.newOutputStream(path)) {
                workbook.write(output);
            }
        }
        return path;
    }

    private static void table(
            Workbook workbook,
            String name,
            List<String> headers,
            List<List<String>> rows) {
        Sheet sheet = workbook.createSheet(name);
        sheet.createRow(0).createCell(0).setCellValue(name);
        Row header = sheet.createRow(2);
        for (int column = 0; column < headers.size(); column++) {
            header.createCell(column).setCellValue(headers.get(column));
        }
        for (int index = 0; index < rows.size(); index++) {
            Row row = sheet.createRow(index + 3);
            List<String> values = rows.get(index);
            for (int column = 0; column < values.size(); column++) {
                row.createCell(column).setCellValue(values.get(column));
            }
        }
    }
}
