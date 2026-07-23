package com.orgmemory.worker.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.Charset;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceDocumentReaderTests {

    @TempDir
    Path temporaryDirectory;

    private final SourceDocumentReader reader = new SourceDocumentReader();

    @Test
    void readsAndNormalizesPlainText() throws Exception {
        Path file = temporaryDirectory.resolve("workflow.txt");
        Files.writeString(file, "First step.\r\n\r\n\r\nSecond\tstep.");

        ParsedSource parsed = reader.read(file, file.getFileName().toString());

        assertEquals("text/plain", parsed.detectedMediaType());
        assertEquals("First step.\n\nSecond step.", parsed.normalizedText());
        assertEquals(1, parsed.documents().size());
    }

    @Test
    void rejectsUnsupportedBinaryContent() throws Exception {
        Path file = temporaryDirectory.resolve("payload.exe");
        Files.write(file, new byte[] {'M', 'Z', 0, 0, 1, 2});

        RejectedSourceException failure = assertThrows(
                RejectedSourceException.class,
                () -> reader.read(file, file.getFileName().toString()));

        assertEquals("UNSUPPORTED_MEDIA_TYPE", failure.code());
    }

    @Test
    void routesDetectedOoxmlThroughTheDocumentReaderEvenWithATextExtension() throws Exception {
        Path file = temporaryDirectory.resolve("renamed.txt");
        try (var document = new XWPFDocument(); var output = Files.newOutputStream(file)) {
            document.createParagraph().createRun().setText("Review the request before approval.");
            document.write(output);
        }

        ParsedSource parsed = reader.read(file, file.getFileName().toString());

        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                parsed.detectedMediaType());
        assertEquals("Review the request before approval.", parsed.normalizedText());
    }

    @Test
    void letsTikaDetectLegacyPlainTextEncoding() throws Exception {
        Path file = temporaryDirectory.resolve("legacy.txt");
        String content = "Résumé – café. ".repeat(20);
        Files.write(
                file,
                content.getBytes(Charset.forName("windows-1252")));

        ParsedSource parsed = reader.read(file, file.getFileName().toString());

        assertEquals(content.strip(), parsed.normalizedText());
    }
}
