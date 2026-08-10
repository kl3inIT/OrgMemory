package com.orgmemory.integrations.documentparsing.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.parsing.DocumentBlockKind;
import com.orgmemory.graphrag.parsing.DocumentParseException;
import com.orgmemory.graphrag.parsing.DocumentParseRequest;
import com.orgmemory.graphrag.parsing.DocumentParseResult;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextBox;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class SupportedFormatParsingTests {

    private final SpringAiDocumentParser parser = new SpringAiDocumentParser();

    @Test
    void publishesTheCompleteReusableParserCapability() {
        assertEquals(
                Set.of(
                        "csv", "doc", "docx", "htm", "html", "md", "odp", "ods", "odt",
                        "pdf", "ppt", "pptx", "rtf", "txt", "xls", "xlsx"),
                parser.supportedSuffixes());
    }

    @Test
    void parsesNewOfficeAndOpenDocumentFormatsEndToEnd() throws Exception {
        Map<String, byte[]> fixtures = Map.ofEntries(
                Map.entry("report.xlsx", spreadsheet(true)),
                Map.entry("report.xls", spreadsheet(false)),
                Map.entry("policy.doc", legacyWord()),
                Map.entry("briefing.pptx", presentation(true)),
                Map.entry("briefing.ppt", presentation(false)),
                Map.entry("policy.odt", openDocument("text")),
                Map.entry("headcount.ods", openDocument("spreadsheet")),
                Map.entry("briefing.odp", openDocument("presentation")),
                Map.entry("policy.rtf", rtf()),
                Map.entry("wiki.html", html()),
                Map.entry("wiki.htm", html()));

        for (var fixture : fixtures.entrySet()) {
            DocumentParseResult parsed;
            try {
                parsed = parse(fixture.getKey(), fixture.getValue());
            } catch (DocumentParseException failure) {
                throw new AssertionError(fixture.getKey() + " did not parse", failure);
            }
            String expectedText = fixture.getKey().endsWith(".doc")
                    ? "Legacy policy evidence"
                    : "ORGMEMORY_EVIDENCE";
            assertTrue(
                    parsed.document().content().contains(expectedText),
                    () -> fixture.getKey() + " lost its evidence text: "
                            + parsed.document().content());
        }
    }

    @Test
    void readsBomSemicolonCsvWithQuotedNewlinesAsOneTypedTable() {
        byte[] bytes = ("\ufeffTeam;Note;Count\r\n"
                        + "Platform;\"first line\nsecond line\";2\r\n")
                .getBytes(StandardCharsets.UTF_8);

        DocumentParseResult parsed = parse("headcount.csv", bytes);

        assertEquals("text/csv", parsed.detectedMediaType());
        assertEquals(1, parsed.document().blocks().size());
        assertEquals(DocumentBlockKind.TABLE, parsed.document().blocks().getFirst().kind());
        assertEquals(
                "Team\tNote\tCount\nPlatform\tfirst line second line\t2",
                parsed.document().content());
    }

    @Test
    void removesHtmlNavigationScriptAndStyleBeforeCreatingEvidence() {
        DocumentParseResult parsed = parse("wiki.html", html());

        assertTrue(parsed.document().content().contains("ORGMEMORY_EVIDENCE"));
        assertFalse(parsed.document().content().contains("NAVIGATION_SECRET"));
        assertFalse(parsed.document().content().contains("SCRIPT_SECRET"));
        assertFalse(parsed.document().content().contains("STYLE_SECRET"));
    }

    @Test
    void refusesDeclaredArchivesButStillAcceptsZipBasedOfficeAndOpenDocumentFiles()
            throws Exception {
        byte[] archive = zip(Map.of("payload.txt", "hidden"));

        assertEquals(
                "UNSUPPORTED_MEDIA_TYPE",
                assertThrows(DocumentParseException.class, () -> parse("payload.zip", archive))
                        .code());
        assertEquals(
                "MEDIA_TYPE_MISMATCH",
                assertThrows(DocumentParseException.class, () -> parse("payload.docx", archive))
                        .code());
        assertTrue(parse("report.xlsx", spreadsheet(true)).document().hasStructuredBlocks());
        assertTrue(parse("report.ods", openDocument("spreadsheet")).document().hasStructuredBlocks());
    }

    private DocumentParseResult parse(String fileName, byte[] bytes) {
        return parser.parse(new DocumentParseRequest(
                fileName, "application/octet-stream", bytes, Optional.empty()));
    }

    private static byte[] spreadsheet(boolean openXml) throws Exception {
        try (var workbook = openXml ? new XSSFWorkbook() : new HSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("ORGMEMORY_EVIDENCE");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Team");
            header.createCell(1).setCellValue("Count");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("ORGMEMORY_EVIDENCE");
            row.createCell(1).setCellValue(42);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] presentation(boolean openXml) throws Exception {
        try (var output = new ByteArrayOutputStream()) {
            if (openXml) {
                try (var slides = new XMLSlideShow()) {
                    var slide = slides.createSlide();
                    XSLFTextBox box = slide.createTextBox();
                    box.setText("ORGMEMORY_EVIDENCE");
                    box.setAnchor(new Rectangle2D.Double(10, 10, 300, 100));
                    slides.write(output);
                }
            } else {
                try (var slides = new HSLFSlideShow()) {
                    var slide = slides.createSlide();
                    HSLFTextBox box = new HSLFTextBox();
                    box.setText("ORGMEMORY_EVIDENCE");
                    box.setAnchor(new Rectangle2D.Double(10, 10, 300, 100));
                    slide.addShape(box);
                    slides.write(output);
                }
            }
            return output.toByteArray();
        }
    }

    private static byte[] openDocument(String kind) throws Exception {
        String mediaType = "application/vnd.oasis.opendocument." + kind;
        String officeBody = switch (kind) {
            case "spreadsheet" -> """
                    <office:spreadsheet><table:table table:name="Evidence"><table:table-row>
                    <table:table-cell office:value-type="string"><text:p>ORGMEMORY_EVIDENCE</text:p></table:table-cell>
                    </table:table-row></table:table></office:spreadsheet>
                    """;
            case "presentation" -> """
                    <office:presentation><draw:page draw:name="Evidence"><draw:frame>
                    <draw:text-box><text:p>ORGMEMORY_EVIDENCE</text:p></draw:text-box>
                    </draw:frame></draw:page></office:presentation>
                    """;
            default -> "<office:text><text:p>ORGMEMORY_EVIDENCE</text:p></office:text>";
        };
        String content = """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document-content
                  xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                  xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
                  xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
                  xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
                  office:version="1.2"><office:body>%s</office:body></office:document-content>
                """.formatted(officeBody);
        try (var output = new ByteArrayOutputStream();
                var zip = new ZipOutputStream(output)) {
            byte[] typeBytes = mediaType.getBytes(StandardCharsets.UTF_8);
            CRC32 crc = new CRC32();
            crc.update(typeBytes);
            ZipEntry mimetype = new ZipEntry("mimetype");
            mimetype.setMethod(ZipEntry.STORED);
            mimetype.setSize(typeBytes.length);
            mimetype.setCompressedSize(typeBytes.length);
            mimetype.setCrc(crc.getValue());
            zip.putNextEntry(mimetype);
            zip.write(typeBytes);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        }
    }

    private static byte[] rtf() {
        return ("{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}"
                        + "\\f0\\fs24 ORGMEMORY_EVIDENCE\\par}")
                .getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] legacyWord() throws Exception {
        try (var encoded = SupportedFormatParsingTests.class
                        .getResourceAsStream("/legacy-word.doc.gz.b64");
                var gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(
                        Base64.getMimeDecoder().decode(encoded.readAllBytes())))) {
            return gzip.readAllBytes();
        }
    }

    private static byte[] html() {
        return """
                <!doctype html><html><body><nav>NAVIGATION_SECRET</nav>
                <script>SCRIPT_SECRET</script><style>.STYLE_SECRET { color: red; }</style>
                <main><h1>ORGMEMORY_EVIDENCE</h1><p>Approved policy.</p></main></body></html>
                """.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] zip(Map<String, String> entries) throws Exception {
        try (var output = new ByteArrayOutputStream();
                var zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }
}
