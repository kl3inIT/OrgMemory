package com.orgmemory.core.knowledge;

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
    PDF(Set.of("pdf"), "application/pdf", "application/pdf", true, true),
    WORD(
            Set.of("docx"),
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            true,
            false),
    POWERPOINT(
            Set.of("pptx"),
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            true,
            false),
    MARKDOWN(Set.of("md"), "text/markdown", "text/plain", true, true),
    TEXT(Set.of("txt"), "text/plain", "text/plain", true, true),
    PNG(Set.of("png"), "image/png", "image/png", false, true),
    JPEG(Set.of("jpg", "jpeg"), "image/jpeg", "image/jpeg", false, true),
    GIF(Set.of("gif"), "image/gif", "image/gif", false, true),
    WEBP(Set.of("webp"), "image/webp", "image/webp", false, true);

    private final Set<String> extensions;
    private final String canonicalMediaType;
    private final String browserSafeMediaType;
    private final boolean uploadAllowed;
    private final boolean inlinePreviewAllowed;

    KnowledgeContentType(
            Set<String> extensions,
            String canonicalMediaType,
            String browserSafeMediaType,
            boolean uploadAllowed,
            boolean inlinePreviewAllowed) {
        this.extensions = Set.copyOf(extensions);
        this.canonicalMediaType = canonicalMediaType;
        this.browserSafeMediaType = browserSafeMediaType;
        this.uploadAllowed = uploadAllowed;
        this.inlinePreviewAllowed = inlinePreviewAllowed;
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
}
