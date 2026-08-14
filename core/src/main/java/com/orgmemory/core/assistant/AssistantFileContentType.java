package com.orgmemory.core.assistant;

import com.orgmemory.core.shared.error.BusinessValidationException;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

final class AssistantFileContentType {

    private static final long MAXIMUM_BYTES = 25L * 1024 * 1024;
    private static final Map<String, Type> TYPES = Map.ofEntries(
            Map.entry("txt", new Type("text/plain", "text/plain", true, Signature.TEXT)),
            Map.entry("md", new Type("text/markdown", "text/plain", true, Signature.TEXT)),
            Map.entry("csv", new Type("text/csv", "text/plain", true, Signature.TEXT)),
            Map.entry("html", new Type("text/html", "text/plain", false, Signature.TEXT)),
            Map.entry("htm", new Type("text/html", "text/plain", false, Signature.TEXT)),
            Map.entry("rtf", new Type("application/rtf", "text/plain", false, Signature.TEXT)),
            Map.entry("pdf", new Type("application/pdf", "application/pdf", true, Signature.PDF)),
            Map.entry("doc", new Type("application/msword", "application/msword", false, Signature.OLE)),
            Map.entry("xls", new Type("application/vnd.ms-excel", "application/vnd.ms-excel", false, Signature.OLE)),
            Map.entry("ppt", new Type("application/vnd.ms-powerpoint", "application/vnd.ms-powerpoint", false, Signature.OLE)),
            Map.entry("docx", new Type("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", false, Signature.ZIP_CONTAINER)),
            Map.entry("xlsx", new Type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", false, Signature.ZIP_CONTAINER)),
            Map.entry("pptx", new Type("application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/vnd.openxmlformats-officedocument.presentationml.presentation", false, Signature.ZIP_CONTAINER)),
            Map.entry("odt", new Type("application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.text", false, Signature.ZIP_CONTAINER)),
            Map.entry("ods", new Type("application/vnd.oasis.opendocument.spreadsheet", "application/vnd.oasis.opendocument.spreadsheet", false, Signature.ZIP_CONTAINER)),
            Map.entry("odp", new Type("application/vnd.oasis.opendocument.presentation", "application/vnd.oasis.opendocument.presentation", false, Signature.ZIP_CONTAINER)));

    private AssistantFileContentType() {}

    static String safeFileName(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("assistant.file-filename-invalid", "file name is required");
        }
        try {
            String portable = value.replace('\\', '/');
            String candidate = portable.substring(portable.lastIndexOf('/') + 1);
            Path name = Path.of(candidate).getFileName();
            String safe = name == null ? "" : name.toString().strip().replaceAll("\\p{Cntrl}", "_");
            if (safe.isBlank() || safe.equals(".") || safe.equals("..") || safe.length() > 255) {
                throw invalid("assistant.file-filename-invalid", "file name is invalid");
            }
            return safe;
        } catch (InvalidPathException invalidPath) {
            throw new BusinessValidationException(
                    "assistant.file-filename-invalid", "file name is invalid", invalidPath);
        }
    }

    static Type require(String fileName, long contentLength) {
        if (contentLength <= 0 || contentLength > MAXIMUM_BYTES) {
            throw invalid(
                    "assistant.file-size-invalid",
                    "file size must be between 1 byte and 25 MB");
        }
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        Type type = TYPES.get(extension);
        if (type == null) {
            throw invalid("assistant.file-type-unsupported", "file type is not supported");
        }
        return type;
    }

    static PushbackInputStream validateSignature(Type type, java.io.InputStream content) {
        PushbackInputStream input = new PushbackInputStream(content, 8);
        byte[] prefix = new byte[8];
        int count;
        try {
            count = input.read(prefix);
            if (count > 0) {
                input.unread(prefix, 0, count);
            }
        } catch (IOException failure) {
            throw invalid("assistant.file-unreadable", "file could not be read");
        }
        boolean valid = switch (type.signature()) {
            case TEXT -> count > 0 && !containsNull(prefix, count);
            case PDF -> startsWith(prefix, count, new int[] {0x25, 0x50, 0x44, 0x46, 0x2d});
            case OLE -> startsWith(prefix, count, new int[] {0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1});
            case ZIP_CONTAINER -> startsWith(prefix, count, new int[] {0x50, 0x4b, 0x03, 0x04});
        };
        if (!valid) {
            throw invalid("assistant.file-type-mismatch", "file content does not match its extension");
        }
        return input;
    }

    private static boolean containsNull(byte[] bytes, int count) {
        for (int index = 0; index < count; index++) {
            if (bytes[index] == 0) return true;
        }
        return false;
    }

    private static boolean startsWith(byte[] actual, int count, int[] expected) {
        if (count < expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if ((actual[index] & 0xff) != expected[index]) return false;
        }
        return true;
    }

    private static BusinessValidationException invalid(String code, String message) {
        return new BusinessValidationException(code, message);
    }

    record Type(
            String mediaType,
            String browserSafeMediaType,
            boolean inlinePreviewAllowed,
            Signature signature) {}
    private enum Signature { TEXT, PDF, OLE, ZIP_CONTAINER }
}
