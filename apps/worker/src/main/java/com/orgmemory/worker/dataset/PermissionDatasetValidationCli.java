package com.orgmemory.worker.dataset;

import com.orgmemory.core.permission.KnowledgePermissionPolicy;
import com.orgmemory.core.permission.dataset.PermissionDataset;
import com.orgmemory.core.permission.dataset.PermissionDatasetIssue;
import com.orgmemory.core.permission.dataset.PermissionDatasetValidationReport;
import com.orgmemory.core.permission.dataset.PermissionDatasetValidator;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class PermissionDatasetValidationCli {

    private PermissionDatasetValidationCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream output, PrintStream error) {
        if (args == null || args.length != 1) {
            error.println("Usage: validatePermissionDataset --args=\"<workbook.xlsx>\"");
            return 2;
        }

        try {
            PermissionDataset dataset = new XlsxPermissionDatasetReader().read(Path.of(args[0]));
            PermissionDatasetValidationReport report = new PermissionDatasetValidator(
                    new KnowledgePermissionPolicy()).validate(dataset);
            output.printf(
                    "Permission dataset: documents=%d metadata=%d users=%d evaluations=%d evaluated=%d matched=%d errors=%d%n",
                    report.documentCount(),
                    report.metadataCount(),
                    report.userCount(),
                    report.evaluationCount(),
                    report.evaluatedCases(),
                    report.matchedExpectations(),
                    report.errorCount());
            for (PermissionDatasetIssue issue : report.issues()) {
                output.printf(
                        "%s %s [%s] %s%n",
                        issue.severity(),
                        issue.code(),
                        issue.location(),
                        issue.message());
            }
            return report.valid() ? 0 : 1;
        } catch (IOException | InvalidPathException | PermissionDatasetFormatException exception) {
            error.println("Unable to validate permission dataset: " + exception.getMessage());
            return 2;
        }
    }
}
