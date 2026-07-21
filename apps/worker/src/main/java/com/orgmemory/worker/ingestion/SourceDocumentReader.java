package com.orgmemory.worker.ingestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.tika.Tika;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

@Component
class SourceDocumentReader {

    private static final List<String> ALLOWED_MEDIA_TYPES = List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/markdown");

    private final Tika detector = new Tika();

    ParsedSource read(Path file, String fileName) throws IOException {
        String detectedMediaType;
        try (var content = Files.newInputStream(file)) {
            detectedMediaType = detector.detect(content, fileName).toLowerCase(Locale.ROOT);
        }
        if (!isAllowed(detectedMediaType, fileName)) {
            throw new RejectedSourceException(
                    "UNSUPPORTED_MEDIA_TYPE", "The uploaded file type is not supported");
        }
        var resource = new FileSystemResource(file);
        List<Document> documents;
        if ("application/pdf".equals(detectedMediaType)) {
            documents = new PagePdfDocumentReader(resource).get();
        } else if (isPlainText(detectedMediaType, fileName)) {
            documents = List.of(new Document(Files.readString(file, StandardCharsets.UTF_8)));
        } else {
            documents = new TikaDocumentReader(resource).get();
        }
        List<Document> nonEmpty = documents.stream()
                .filter(document -> document.getText() != null && !document.getText().isBlank())
                .toList();
        if (nonEmpty.isEmpty()) {
            throw new RejectedSourceException("NO_EXTRACTABLE_TEXT", "No extractable text was found");
        }
        String normalizedText = nonEmpty.stream()
                .map(Document::getText)
                .map(text -> normalize(Objects.requireNonNull(text)))
                .filter(text -> !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElseThrow(() -> new RejectedSourceException(
                        "NO_EXTRACTABLE_TEXT", "No extractable text was found"));
        return new ParsedSource(nonEmpty, normalizedText, detectedMediaType);
    }

    private static boolean isAllowed(String mediaType, String fileName) {
        if (ALLOWED_MEDIA_TYPES.contains(mediaType)) {
            return true;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return mediaType.startsWith("text/") && (lower.endsWith(".txt") || lower.endsWith(".md"));
    }

    private static boolean isPlainText(String mediaType, String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return mediaType.startsWith("text/") || lower.endsWith(".txt") || lower.endsWith(".md");
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
