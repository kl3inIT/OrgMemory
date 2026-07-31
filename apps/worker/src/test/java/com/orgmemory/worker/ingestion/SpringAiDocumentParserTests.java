package com.orgmemory.worker.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.graphrag.parsing.DocumentParseRequest;
import com.orgmemory.graphrag.parsing.DocumentParseResult;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringAiDocumentParserTests {

    @TempDir
    Path temporaryDirectory;

    private final SpringAiDocumentParser parser = new SpringAiDocumentParser();

    @Test
    void readsAndNormalizesPlainText() throws Exception {
        Path file = temporaryDirectory.resolve("workflow.txt");
        Files.writeString(file, "First step.\r\n\r\n\r\nSecond\tstep.");

        DocumentParseResult parsed = parse(file);

        assertEquals("text/plain", parsed.detectedMediaType());
        assertEquals("First step.\n\nSecond step.", parsed.document().content());
        assertEquals(1, parsed.document().blocks().size());
    }

    @Test
    void rejectsUnsupportedBinaryContent() throws Exception {
        Path file = temporaryDirectory.resolve("payload.exe");
        Files.write(file, new byte[] {'M', 'Z', 0, 0, 1, 2});

        RejectedSourceException failure = assertThrows(
                RejectedSourceException.class,
                () -> parse(file));

        assertEquals("UNSUPPORTED_MEDIA_TYPE", failure.code());
    }

    @Test
    void routesDetectedOoxmlThroughTheDocumentReaderEvenWithATextExtension() throws Exception {
        Path file = temporaryDirectory.resolve("renamed.txt");
        try (var document = new XWPFDocument(); var output = Files.newOutputStream(file)) {
            document.createParagraph().createRun().setText("Review the request before approval.");
            document.write(output);
        }

        DocumentParseResult parsed = parse(file);

        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                parsed.detectedMediaType());
        assertEquals("Review the request before approval.", parsed.document().content());
    }

    @Test
    void letsTikaDetectWindows1252PlainTextEncoding() throws Exception {
        Path file = temporaryDirectory.resolve("windows-1252.txt");
        String content = "Résumé – café. ".repeat(20);
        Files.write(
                file,
                content.getBytes(Charset.forName("windows-1252")));

        DocumentParseResult parsed = parse(file);

        assertEquals(content.strip(), parsed.document().content());
    }

    private DocumentParseResult parse(Path file) throws Exception {
        return parser.parse(new DocumentParseRequest(
                file.getFileName().toString(),
                "application/octet-stream",
                Files.readAllBytes(file),
                Optional.empty()));
    }
}
