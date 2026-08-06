package com.orgmemory.worker.ingestion;

import com.orgmemory.graphrag.parsing.CanonicalDocument;
import com.orgmemory.graphrag.parsing.DocumentBlock;
import com.orgmemory.graphrag.parsing.DocumentParseRequest;
import com.orgmemory.graphrag.parsing.DocumentParseResult;
import com.orgmemory.graphrag.parsing.DocumentParser;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import com.orgmemory.graphrag.processing.ResolvedDocumentProcessingProfile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.txt.CharsetDetector;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

@Component
class SpringAiDocumentParser implements DocumentParser {

    /**
     * Bumped from 2.0.0 when the reader began emitting typed blocks instead of
     * flat text. The version is part of the canonical processing profile that
     * {@code profileSha256} is computed over, so identical bytes parsed under
     * different behaviour must not share one hash.
     */
    static final ProcessingComponentRef COMPONENT =
            new ProcessingComponentRef("spring-ai-document-reader", "2.1.0");

    private static final List<String> ALLOWED_MEDIA_TYPES = List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/markdown");

    private final Tika detector = new Tika();

    @Override
    public ProcessingComponentRef component() {
        return COMPONENT;
    }

    @Override
    public DocumentParseResult parse(DocumentParseRequest request) {
        try {
            ParsedSource parsed = read(request.content(), request.fileName());
            return new DocumentParseResult(
                    canonical(parsed.blocks()),
                    parsed.detectedMediaType(),
                    Map.of("engine", component().toString()));
        } catch (IOException exception) {
            throw new DocumentParsingException(
                    "could not read source " + request.fileName(), exception);
        }
    }

    private ParsedSource read(byte[] bytes, String fileName) throws IOException {
        String detectedMediaType;
        try (var content = new ByteArrayInputStream(bytes)) {
            detectedMediaType = detector.detect(content, fileName).toLowerCase(Locale.ROOT);
        }
        if (!isAllowed(detectedMediaType, fileName)) {
            throw new RejectedSourceException(
                    "UNSUPPORTED_MEDIA_TYPE", "The uploaded file type is not supported");
        }
        List<ParsedBlock> blocks;
        if ("application/pdf".equals(detectedMediaType)) {
            blocks = readPdf(bytes, fileName);
        } else if (detectedMediaType.startsWith("text/")) {
            blocks = readPlainText(bytes);
        } else {
            blocks = readStructured(bytes, fileName);
        }
        List<ParsedBlock> extracted = blocks.stream()
                .filter(block -> !block.text().isBlank())
                .toList();
        if (extracted.isEmpty()) {
            throw new RejectedSourceException("NO_EXTRACTABLE_TEXT", "No extractable text was found");
        }
        return new ParsedSource(extracted, detectedMediaType);
    }

    /** Pages stay one block each, because page provenance is what a PDF citation needs. */
    private static List<ParsedBlock> readPdf(byte[] bytes, String fileName) {
        List<Document> pages =
                new PagePdfDocumentReader(new NamedByteArrayResource(bytes, fileName)).get();
        List<ParsedBlock> blocks = new ArrayList<>();
        for (Document page : pages) {
            String text = page.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            blocks.add(ParsedBlock.paragraph(
                    BlockText.prose(text),
                    number(page, PagePdfDocumentReader.METADATA_START_PAGE_NUMBER),
                    number(page, PagePdfDocumentReader.METADATA_END_PAGE_NUMBER)));
        }
        return blocks;
    }

    private static List<ParsedBlock> readPlainText(byte[] bytes) throws IOException {
        var match = new CharsetDetector().setText(bytes).detect();
        if (match == null) {
            throw new RejectedSourceException(
                    "UNSUPPORTED_TEXT_ENCODING",
                    "The plain-text encoding could not be detected");
        }
        return List.of(ParsedBlock.paragraph(BlockText.prose(match.getString())));
    }

    private static List<ParsedBlock> readStructured(byte[] bytes, String fileName)
            throws IOException {
        XhtmlBlockHandler handler = new XhtmlBlockHandler();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        ParseContext context = new ParseContext();
        // Refuse to follow embedded resources. A supported container must not
        // become a way to have arbitrary attached content parsed.
        context.set(Parser.class, EmptyParser.INSTANCE);
        try (var content = new ByteArrayInputStream(bytes)) {
            new AutoDetectParser().parse(content, handler, metadata, context);
        } catch (org.apache.tika.exception.TikaException | org.xml.sax.SAXException exception) {
            throw new DocumentParsingException("could not extract " + fileName, exception);
        }
        return handler.blocks();
    }

    private static CanonicalDocument canonical(List<ParsedBlock> parsed) {
        StringBuilder content = new StringBuilder();
        List<DocumentBlock> blocks = new ArrayList<>();
        for (ParsedBlock block : parsed) {
            if (!content.isEmpty()) {
                content.append("\n\n");
            }
            int start = content.length();
            content.append(block.text());
            int end = content.length();
            blocks.add(new DocumentBlock(
                    blocks.size(),
                    block.kind(),
                    start,
                    end,
                    block.startPage(),
                    block.endPage(),
                    block.headingLevel(),
                    block.attributes()));
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

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String fileName;

        private NamedByteArrayResource(byte[] bytes, String fileName) {
            super(bytes);
            this.fileName = Objects.requireNonNull(fileName, "fileName");
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }
}
