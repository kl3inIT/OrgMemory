package com.orgmemory.worker.dataset;

import com.orgmemory.core.permission.AccessOutcome;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.permission.KnowledgeResource;
import com.orgmemory.core.permission.KnowledgeRole;
import com.orgmemory.core.permission.KnowledgeSubject;
import com.orgmemory.core.permission.dataset.DatasetEvaluation;
import com.orgmemory.core.permission.dataset.DatasetPermissionRule;
import com.orgmemory.core.permission.dataset.PermissionDataset;
import com.orgmemory.core.permission.dataset.PermissionMatrixEntry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public class XlsxPermissionDatasetReader {

    private static final String DATASET_ORGANIZATION = "permission-dataset";
    private static final long MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024;
    private static final int MAX_DATA_ROWS_PER_SHEET = 10_000;
    private static final int MAX_HEADER_SEARCH_ROWS = 10;

    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

    public PermissionDataset read(Path path) throws IOException, PermissionDatasetFormatException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new PermissionDatasetFormatException("Workbook does not exist: " + path);
        }
        if (Files.size(path) > MAX_FILE_SIZE_BYTES) {
            throw new PermissionDatasetFormatException("Workbook exceeds the 25 MiB validation limit.");
        }
        if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new PermissionDatasetFormatException("Permission dataset must be an .xlsx workbook.");
        }

        try (InputStream input = Files.newInputStream(path); Workbook workbook = WorkbookFactory.create(input)) {
            return readWorkbook(workbook);
        } catch (PermissionDatasetFormatException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PermissionDatasetFormatException("Unable to parse workbook: " + exception.getMessage(), exception);
        }
    }

    PermissionDataset readWorkbook(Workbook workbook) throws PermissionDatasetFormatException {
        Table documents = table(workbook, "Documents", Set.of("document_id"));
        Table metadata = table(
                workbook,
                "Document_Metadata",
                Set.of("document_id", "department", "classification", "allowed_access"));
        Table users = table(workbook, "Users", Set.of("user_id", "department", "role", "status"));
        Table departments = table(workbook, "Departments", Set.of("department_id", "department_en"));
        Table roles = table(workbook, "Roles", Set.of("role_en"));
        Table permissions = table(
                workbook,
                "Permissions",
                Set.of("classification", "employee", "manager", "director", "executive"));
        Table evaluations = table(
                workbook,
                "Public_Evaluation",
                Set.of("question_id", "user_id", "expected_permission", "expected_document_id"));

        DepartmentDirectory departmentDirectory = readDepartments(departments);
        return new PermissionDataset(
                readColumn(documents, "document_id"),
                departmentDirectory.canonicalIds(),
                readRoles(roles),
                readResources(metadata, departmentDirectory),
                readSubjects(users, departmentDirectory),
                readPermissionMatrix(permissions),
                readEvaluations(evaluations));
    }

    private DepartmentDirectory readDepartments(Table table) throws PermissionDatasetFormatException {
        List<String> canonicalIds = new ArrayList<>();
        Map<String, String> aliases = new HashMap<>();
        for (TableRow row : rows(table)) {
            String departmentId = required(row, "department_id");
            String departmentName = required(row, "department_en");
            canonicalIds.add(departmentId);
            addDepartmentAlias(aliases, departmentId, departmentId, row);
            addDepartmentAlias(aliases, departmentName, departmentId, row);
        }
        return new DepartmentDirectory(List.copyOf(canonicalIds), Map.copyOf(aliases));
    }

    private static void addDepartmentAlias(
            Map<String, String> aliases,
            String alias,
            String departmentId,
            TableRow row) throws PermissionDatasetFormatException {
        String key = normalized(alias);
        String previous = aliases.putIfAbsent(key, departmentId);
        if (previous != null && !previous.equals(departmentId)) {
            throw format(row, "department_en", "Department alias is ambiguous: " + alias + ".");
        }
    }

    private List<String> readColumn(Table table, String column) throws PermissionDatasetFormatException {
        List<String> values = new ArrayList<>();
        for (TableRow row : rows(table)) {
            values.add(required(row, column));
        }
        return values;
    }

    private List<KnowledgeRole> readRoles(Table table) throws PermissionDatasetFormatException {
        List<KnowledgeRole> roles = new ArrayList<>();
        for (TableRow row : rows(table)) {
            roles.add(parse(
                    row,
                    "role_en",
                    KnowledgeRole::fromDatasetValue,
                    "knowledge role"));
        }
        return roles;
    }

    private List<KnowledgeResource> readResources(
            Table table,
            DepartmentDirectory departments) throws PermissionDatasetFormatException {
        List<KnowledgeResource> resources = new ArrayList<>();
        for (TableRow row : rows(table)) {
            resources.add(new KnowledgeResource(
                    DATASET_ORGANIZATION,
                    required(row, "document_id"),
                    departments.resolve(optional(row, "department")),
                    parse(
                            row,
                            "classification",
                            KnowledgeClassification::fromDatasetValue,
                            "classification"),
                    parse(
                            row,
                            "allowed_access",
                            DeclaredAccessScope::fromDatasetValue,
                            "declared access scope")));
        }
        return resources;
    }

    private List<KnowledgeSubject> readSubjects(
            Table table,
            DepartmentDirectory departments) throws PermissionDatasetFormatException {
        List<KnowledgeSubject> subjects = new ArrayList<>();
        for (TableRow row : rows(table)) {
            String status = required(row, "status");
            if (!status.equalsIgnoreCase("Active") && !status.equalsIgnoreCase("Inactive")) {
                throw format(row, "status", "Expected Active or Inactive, found " + status + ".");
            }
            subjects.add(new KnowledgeSubject(
                    DATASET_ORGANIZATION,
                    required(row, "user_id"),
                    departments.resolve(optional(row, "department")),
                    parse(row, "role", KnowledgeRole::fromDatasetValue, "knowledge role"),
                    status.equalsIgnoreCase("Active")));
        }
        return subjects;
    }

    private List<PermissionMatrixEntry> readPermissionMatrix(Table table)
            throws PermissionDatasetFormatException {
        List<PermissionMatrixEntry> entries = new ArrayList<>();
        Map<String, KnowledgeRole> roleColumns = Map.of(
                "employee", KnowledgeRole.EMPLOYEE,
                "manager", KnowledgeRole.MANAGER,
                "director", KnowledgeRole.DIRECTOR,
                "executive", KnowledgeRole.EXECUTIVE);
        for (TableRow row : rows(table)) {
            KnowledgeClassification classification = parse(
                    row,
                    "classification",
                    KnowledgeClassification::fromDatasetValue,
                    "classification");
            for (Map.Entry<String, KnowledgeRole> roleColumn : roleColumns.entrySet()) {
                entries.add(new PermissionMatrixEntry(
                        classification,
                        roleColumn.getValue(),
                        parse(
                                row,
                                roleColumn.getKey(),
                                DatasetPermissionRule::fromDatasetValue,
                                "permission rule")));
            }
        }
        return entries;
    }

    private List<DatasetEvaluation> readEvaluations(Table table) throws PermissionDatasetFormatException {
        List<DatasetEvaluation> evaluations = new ArrayList<>();
        for (TableRow row : rows(table)) {
            evaluations.add(new DatasetEvaluation(
                    required(row, "question_id"),
                    required(row, "user_id"),
                    List.of(required(row, "expected_document_id").split("\\s*;\\s*")),
                    parse(
                            row,
                            "expected_permission",
                            value -> AccessOutcome.valueOf(value.trim().toUpperCase(Locale.ROOT)),
                            "expected permission")));
        }
        return evaluations;
    }

    private Table table(Workbook workbook, String sheetName, Set<String> requiredHeaders)
            throws PermissionDatasetFormatException {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new PermissionDatasetFormatException("Missing required sheet: " + sheetName);
        }
        int lastSearchRow = Math.min(sheet.getLastRowNum(), MAX_HEADER_SEARCH_ROWS - 1);
        for (int rowIndex = 0; rowIndex <= lastSearchRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Map<String, Integer> headers = headers(sheetName, row);
            if (headers.keySet().containsAll(requiredHeaders)) {
                return new Table(sheet, rowIndex, headers);
            }
        }
        throw new PermissionDatasetFormatException(
                "Sheet %s is missing required headers: %s".formatted(sheetName, requiredHeaders));
    }

    private Map<String, Integer> headers(String sheetName, Row row) throws PermissionDatasetFormatException {
        Map<String, Integer> headers = new LinkedHashMap<>();
        for (Cell cell : row) {
            String header = normalized(cellText(sheetName, cell));
            if (header.isEmpty()) {
                continue;
            }
            Integer previous = headers.putIfAbsent(header, cell.getColumnIndex());
            if (previous != null) {
                throw new PermissionDatasetFormatException(
                        "Sheet %s has duplicate header %s at row %d."
                                .formatted(sheetName, header, row.getRowNum() + 1));
            }
        }
        return headers;
    }

    private List<TableRow> rows(Table table) throws PermissionDatasetFormatException {
        int rowCount = table.sheet().getLastRowNum() - table.headerRowIndex();
        if (rowCount > MAX_DATA_ROWS_PER_SHEET) {
            throw new PermissionDatasetFormatException(
                    "Sheet %s exceeds the %,d-row validation limit."
                            .formatted(table.sheet().getSheetName(), MAX_DATA_ROWS_PER_SHEET));
        }
        List<TableRow> rows = new ArrayList<>();
        for (int rowIndex = table.headerRowIndex() + 1; rowIndex <= table.sheet().getLastRowNum(); rowIndex++) {
            Row row = table.sheet().getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<String, Integer> header : table.headers().entrySet()) {
                Cell cell = row.getCell(header.getValue(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                values.put(header.getKey(), cell == null ? "" : cellText(table.sheet().getSheetName(), cell).trim());
            }
            if (values.values().stream().anyMatch(value -> !value.isBlank())) {
                rows.add(new TableRow(table.sheet().getSheetName(), rowIndex + 1, values));
            }
        }
        return rows;
    }

    private String cellText(String sheetName, Cell cell) throws PermissionDatasetFormatException {
        if (cell.getCellType() == CellType.FORMULA) {
            throw new PermissionDatasetFormatException(
                    "Formula cells are not allowed in validation input: %s!R%dC%d."
                            .formatted(sheetName, cell.getRowIndex() + 1, cell.getColumnIndex() + 1));
        }
        return formatter.formatCellValue(cell);
    }

    private static String required(TableRow row, String column) throws PermissionDatasetFormatException {
        String value = optional(row, column);
        if (value == null || value.isBlank()) {
            throw format(row, column, "Required value is blank.");
        }
        return value;
    }

    private static String optional(TableRow row, String column) {
        String value = row.values().get(column);
        return value == null || value.isBlank() ? null : value;
    }

    private static <T> T parse(
            TableRow row,
            String column,
            ValueParser<T> parser,
            String type) throws PermissionDatasetFormatException {
        String value = required(row, column);
        try {
            return parser.parse(value);
        } catch (IllegalArgumentException exception) {
            throw format(row, column, "Invalid " + type + ": " + value + ".", exception);
        }
    }

    private static PermissionDatasetFormatException format(TableRow row, String column, String message) {
        return new PermissionDatasetFormatException(
                "%s row %d column %s: %s".formatted(row.sheetName(), row.excelRow(), column, message));
    }

    private static PermissionDatasetFormatException format(
            TableRow row,
            String column,
            String message,
            Throwable cause) {
        return new PermissionDatasetFormatException(
                "%s row %d column %s: %s".formatted(row.sheetName(), row.excelRow(), column, message),
                cause);
    }

    private static String normalized(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface ValueParser<T> {
        T parse(String value);
    }

    private record Table(Sheet sheet, int headerRowIndex, Map<String, Integer> headers) {
    }

    private record TableRow(String sheetName, int excelRow, Map<String, String> values) {
    }

    private record DepartmentDirectory(List<String> canonicalIds, Map<String, String> aliases) {

        String resolve(String value) {
            if (value == null) {
                return null;
            }
            return aliases.getOrDefault(normalized(value), value);
        }
    }
}
