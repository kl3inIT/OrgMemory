package com.orgmemory.core.knowledge.sourceledger;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Closed content-type policy shared by evidence ingestion and browser delivery.
 *
 * <p>The canonical media type describes stored evidence. Browser delivery has
 * a separate safe media type and disposition so source metadata cannot turn
 * active or unsupported content into same-origin executable content.
 */
public enum KnowledgeContentType {
    PDF(Set.of("pdf"), "application/pdf", "application/pdf", true, true, 25),
    WORD(
            Set.of("docx"),
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            true,
            false,
            25),
    WORD_LEGACY(Set.of("doc"), "application/msword", "application/msword", true, false, 25),
    POWERPOINT(
            Set.of("pptx"),
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            true,
            false,
            25),
    POWERPOINT_LEGACY(
            Set.of("ppt"), "application/vnd.ms-powerpoint", "application/vnd.ms-powerpoint", true, false, 25),
    EXCEL(
            Set.of("xlsx"),
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            true,
            false,
            15),
    EXCEL_LEGACY(
            Set.of("xls"), "application/vnd.ms-excel", "application/vnd.ms-excel", true, false, 15),
    CSV(Set.of("csv"), "text/csv", "text/plain", true, true, 10),
    HTML(Set.of("html", "htm"), "text/html", "text/plain", true, false, 10),
    RTF(Set.of("rtf"), "application/rtf", "text/plain", true, false, 10),
    OPEN_DOCUMENT_TEXT(
            Set.of("odt"), "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.text", true, false, 25),
    OPEN_DOCUMENT_SPREADSHEET(
            Set.of("ods"), "application/vnd.oasis.opendocument.spreadsheet", "application/vnd.oasis.opendocument.spreadsheet", true, false, 15),
    OPEN_DOCUMENT_PRESENTATION(
            Set.of("odp"), "application/vnd.oasis.opendocument.presentation", "application/vnd.oasis.opendocument.presentation", true, false, 25),
    MARKDOWN(Set.of("md"), "text/markdown", "text/plain", true, true, 10),
    TEXT(Set.of("txt"), "text/plain", "text/plain", true, true, 10),
    PNG(Set.of("png"), "image/png", "image/png", false, true, 25),
    JPEG(Set.of("jpg", "jpeg"), "image/jpeg", "image/jpeg", false, true, 25),
    GIF(Set.of("gif"), "image/gif", "image/gif", false, true, 25),
    WEBP(Set.of("webp"), "image/webp", "image/webp", false, true, 25);

    private final Set<String> extensions;
    private final String canonicalMediaType;
    private final String browserSafeMediaType;
    private final boolean uploadAllowed;
    private final boolean inlinePreviewAllowed;
    private final long maximumUploadBytes;

    KnowledgeContentType(
            Set<String> extensions,
            String canonicalMediaType,
            String browserSafeMediaType,
            boolean uploadAllowed,
            boolean inlinePreviewAllowed,
            long maximumUploadMegabytes) {
        this.extensions = Set.copyOf(extensions);
        this.canonicalMediaType = canonicalMediaType;
        this.browserSafeMediaType = browserSafeMediaType;
        this.uploadAllowed = uploadAllowed;
        this.inlinePreviewAllowed = inlinePreviewAllowed;
        this.maximumUploadBytes = maximumUploadMegabytes * 1024 * 1024;
    }

    public static Optional<KnowledgeContentType> fromFileName(String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        String normalized = fileName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String baseName = slash < 0 ? normalized : normalized.substring(slash + 1);
        int dot = baseName.lastIndexOf('.');
        if (dot < 0 || dot == baseName.length() - 1) {
            return Optional.empty();
        }
        String extension =
                baseName.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (KnowledgeContentType candidate : values()) {
            if (candidate.extensions.contains(extension)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public String canonicalMediaType() {
        return canonicalMediaType;
    }

    public String browserSafeMediaType() {
        return browserSafeMediaType;
    }

    public boolean uploadAllowed() {
        return uploadAllowed;
    }

    public boolean inlinePreviewAllowed() {
        return inlinePreviewAllowed;
    }

    public long maximumUploadBytes() {
        return maximumUploadBytes;
    }
}
