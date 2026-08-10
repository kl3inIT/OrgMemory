package com.orgmemory.integrations.documentparsing.springai;

import com.orgmemory.graphrag.parsing.CanonicalDocument;
import com.orgmemory.graphrag.parsing.DocumentBlock;
import com.orgmemory.graphrag.parsing.DocumentParseException;
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
import java.util.Set;
import java.util.zip.ZipInputStream;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.txt.CharsetDetector;
import org.jsoup.Jsoup;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

/** Spring AI and Apache Tika document parser shared by ingestion consumers. */
@Component
public final class SpringAiDocumentParser implements DocumentParser {

    /** Preserved across the module-only move so existing profile hashes remain reproducible. */
    public static final ProcessingComponentRef COMPONENT =
            new ProcessingComponentRef("spring-ai-document-reader", "2.1.0");

    private static final Set<String> SUPPORTED_SUFFIXES = Set.of(
            "csv", "doc", "docx", "htm", "html", "md", "odp", "ods", "odt",
            "pdf", "ppt", "pptx", "rtf", "txt", "xls", "xlsx");

    private static final Map<String, Set<String>> MEDIA_TYPES_BY_SUFFIX = Map.ofEntries(
            Map.entry("csv", Set.of("text/csv", "text/plain")),
            Map.entry("doc", Set.of("application/msword")),
            Map.entry("docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("htm", Set.of("text/html", "application/xhtml+xml")),
            Map.entry("html", Set.of("text/html", "application/xhtml+xml")),
            Map.entry("md", Set.of("text/markdown", "text/plain")),
            Map.entry("odp", Set.of("application/vnd.oasis.opendocument.presentation")),
            Map.entry("ods", Set.of("application/vnd.oasis.opendocument.spreadsheet")),
            Map.entry("odt", Set.of("application/vnd.oasis.opendocument.text")),
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("ppt", Set.of("application/vnd.ms-powerpoint")),
            Map.entry("pptx", Set.of(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation")),
            Map.entry("rtf", Set.of("application/rtf", "text/rtf")),
            Map.entry("txt", Set.of("text/plain")),
            Map.entry("xls", Set.of("application/vnd.ms-excel")),
            Map.entry("xlsx", Set.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));

    private final Tika detector = new Tika();

    @Override
    public ProcessingComponentRef component() {
        return COMPONENT;
    }

    @Override
    public Set<String> supportedSuffixes() {
        return SUPPORTED_SUFFIXES;
    }

    @Override
    public DocumentParseResult parse(DocumentParseRequest request) {
        try {
            ParsedSource parsed = read(request.content(), request.fileName(), request.suffix());
            return new DocumentParseResult(
                    canonical(parsed.blocks()),
                    parsed.detectedMediaType(),
                    Map.of("engine", component().toString()));
        } catch (IOException exception) {
            throw new DocumentParseException(
                    "DOCUMENT_READ_FAILED",
                    "The uploaded document could not be read",
                    exception);
        }
    }

    private ParsedSource read(byte[] bytes, String fileName, String suffix) throws IOException {
        if (!SUPPORTED_SUFFIXES.contains(suffix)) {
            throw new DocumentParseException(
                    "UNSUPPORTED_MEDIA_TYPE", "The uploaded file type is not supported");
        }
        String detectedMediaType;
        try (var content = new ByteArrayInputStream(bytes)) {
            detectedMediaType = detector.detect(content, fileName).toLowerCase(Locale.ROOT);
        }
        if (!MEDIA_TYPES_BY_SUFFIX.get(suffix).contains(detectedMediaType)) {
            throw new DocumentParseException(
                    "MEDIA_TYPE_MISMATCH",
                    "The file content does not match its declared format");
        }
        validateDeclaredContainer(bytes, suffix);
        List<ParsedBlock> blocks;
        if ("csv".equals(suffix)) {
            blocks = List.of(CsvTableReader.read(bytes));
            detectedMediaType = "text/csv";
        } else if ("pdf".equals(suffix)) {
            blocks = readPdf(bytes, fileName);
        } else if ("txt".equals(suffix) || "md".equals(suffix)) {
            blocks = readPlainText(bytes);
        } else if ("html".equals(suffix) || "htm".equals(suffix)) {
            blocks = readStructured(cleanHtml(bytes), fileName);
        } else {
            blocks = readStructured(bytes, fileName);
        }
        List<ParsedBlock> extracted = blocks.stream()
                .filter(block -> !block.text().isBlank())
                .toList();
        if (extracted.isEmpty()) {
            throw new DocumentParseException(
                    "NO_EXTRACTABLE_TEXT", "No extractable text was found");
        }
        return new ParsedSource(extracted, detectedMediaType);
    }

    private static List<ParsedBlock> readPdf(byte[] bytes, String fileName) {
        List<Document> pages =
                new PagePdfDocumentReader(new NamedByteArrayResource(bytes, fileName)).get();
        List<ParsedBlock> blocks = new ArrayList<>();
        for (Document page : pages) {
            String text = page.getText();
            if (text != null && !text.isBlank()) {
                blocks.add(ParsedBlock.paragraph(
                        BlockText.prose(text),
                        number(page, PagePdfDocumentReader.METADATA_START_PAGE_NUMBER),
                        number(page, PagePdfDocumentReader.METADATA_END_PAGE_NUMBER)));
            }
        }
        return blocks;
    }

    private static List<ParsedBlock> readPlainText(byte[] bytes) throws IOException {
        var match = new CharsetDetector().setText(bytes).detect();
        if (match == null) {
            throw new DocumentParseException(
                    "UNSUPPORTED_TEXT_ENCODING",
                    "The plain-text encoding could not be detected");
        }
        return List.of(ParsedBlock.paragraph(BlockText.prose(match.getString())));
    }

    private static byte[] cleanHtml(byte[] bytes) throws IOException {
        var match = new CharsetDetector().setText(bytes).detect();
        if (match == null) {
            throw new DocumentParseException(
                    "UNSUPPORTED_TEXT_ENCODING", "The HTML encoding could not be detected");
        }
        var document = Jsoup.parse(match.getString());
        document.select("nav,script,style").remove();
        return document.outerHtml().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void validateDeclaredContainer(byte[] bytes, String suffix) throws IOException {
        String required = switch (suffix) {
            case "docx" -> "word/document.xml";
            case "xlsx" -> "xl/workbook.xml";
            case "pptx" -> "ppt/presentation.xml";
            default -> null;
        };
        if (required == null) {
            return;
        }
        boolean contentTypes = false;
        boolean payload = false;
        int entries = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > 10_000) {
                    throw new DocumentParseException(
                            "CONTAINER_ENTRY_LIMIT_EXCEEDED",
                            "The document container has too many entries");
                }
                contentTypes |= "[Content_Types].xml".equals(entry.getName());
                payload |= required.equals(entry.getName());
                if (contentTypes && payload) {
                    return;
                }
            }
        }
        throw new DocumentParseException(
                "MEDIA_TYPE_MISMATCH", "The file content does not match its declared format");
    }

    private static List<ParsedBlock> readStructured(byte[] bytes, String fileName)
            throws IOException {
        XhtmlBlockHandler handler = new XhtmlBlockHandler();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        ParseContext context = new ParseContext();
        context.set(Parser.class, EmptyParser.INSTANCE);
        try (var content = new ByteArrayInputStream(bytes)) {
            new AutoDetectParser().parse(content, handler, metadata, context);
        } catch (org.apache.tika.exception.TikaException | org.xml.sax.SAXException exception) {
            throw new DocumentParseException(
                    "DOCUMENT_EXTRACTION_FAILED",
                    "The uploaded document could not be extracted",
                    exception);
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
