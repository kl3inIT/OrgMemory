package com.orgmemory.worker.ingestion;

import com.orgmemory.graphrag.parsing.CanonicalDocument;
import com.orgmemory.graphrag.parsing.DocumentBlock;
import com.orgmemory.graphrag.parsing.DocumentBlockKind;
import com.orgmemory.graphrag.parsing.DocumentParseRequest;
import com.orgmemory.graphrag.parsing.DocumentParseResult;
import com.orgmemory.graphrag.parsing.DocumentParser;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import com.orgmemory.graphrag.processing.ResolvedDocumentProcessingProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.tika.Tika;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

@Component
class SourceDocumentReader implements DocumentParser {

    static final ProcessingComponentRef COMPONENT =
            new ProcessingComponentRef("legacy", "spring-ai-2.0.0");

    private static final List<String> ALLOWED_MEDIA_TYPES = List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/markdown");

    private final Tika detector = new Tika();

    ParsedSource read(Path file, String fileName) throws IOException {
        return read(Files.readAllBytes(file), fileName);
    }

    @Override
    public ProcessingComponentRef component() {
        return COMPONENT;
    }

    @Override
    public DocumentParseResult parse(DocumentParseRequest request) {
        try {
            ParsedSource parsed = read(request.content(), request.fileName());
            return new DocumentParseResult(
                    canonical(parsed.documents()),
                    parsed.detectedMediaType(),
                    Map.of("engine", component().toString()));
        } catch (IOException exception) {
            throw new DocumentParsingException(
                    "could not read source " + request.fileName(), exception);
        }
    }

    private ParsedSource read(byte[] bytes, String fileName) throws IOException {
        String detectedMediaType;
        try (var content = new java.io.ByteArrayInputStream(bytes)) {
            detectedMediaType = detector.detect(content, fileName).toLowerCase(Locale.ROOT);
        }
        if (!isAllowed(detectedMediaType, fileName)) {
            throw new RejectedSourceException(
                    "UNSUPPORTED_MEDIA_TYPE", "The uploaded file type is not supported");
        }
        var resource = new NamedByteArrayResource(bytes, fileName);
        List<Document> documents;
        if ("application/pdf".equals(detectedMediaType)) {
            documents = new PagePdfDocumentReader(resource).get();
        } else if (isPlainText(detectedMediaType)) {
            documents = List.of(new Document(new String(bytes, StandardCharsets.UTF_8)));
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

    private static CanonicalDocument canonical(List<Document> documents) {
        StringBuilder content = new StringBuilder();
        List<DocumentBlock> blocks = new ArrayList<>();
        for (Document document : documents) {
            String body = normalize(Objects.requireNonNull(document.getText()));
            if (body.isBlank()) {
                continue;
            }
            if (!content.isEmpty()) {
                content.append("\n\n");
            }
            int start = content.length();
            content.append(body);
            int end = content.length();
            blocks.add(new DocumentBlock(
                    blocks.size(),
                    DocumentBlockKind.PARAGRAPH,
                    start,
                    end,
                    number(document, PagePdfDocumentReader.METADATA_START_PAGE_NUMBER),
                    number(document, PagePdfDocumentReader.METADATA_END_PAGE_NUMBER),
                    null,
                    Map.of()));
        }
        String canonicalText = content.toString();
        return new CanonicalDocument(
                canonicalText,
                ResolvedDocumentProcessingProfile.sha256(canonicalText),
                blocks);
    }

    private static Integer number(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static boolean isAllowed(String mediaType, String fileName) {
        if (ALLOWED_MEDIA_TYPES.contains(mediaType)) {
            return true;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return mediaType.startsWith("text/") && (lower.endsWith(".txt") || lower.endsWith(".md"));
    }

    private static boolean isPlainText(String mediaType) {
        return mediaType.startsWith("text/");
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String fileName;

        private NamedByteArrayResource(byte[] bytes, String fileName) {
            super(bytes);
            this.fileName = fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }
}
