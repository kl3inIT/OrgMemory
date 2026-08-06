package com.orgmemory.worker.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.parsing.CanonicalDocument;
import com.orgmemory.graphrag.parsing.DocumentBlock;
import com.orgmemory.graphrag.parsing.DocumentBlockKind;
import com.orgmemory.graphrag.parsing.DocumentParseRequest;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/**
 * A PDF citation points at a page, so page provenance must survive the move to
 * typed blocks.
 */
class PdfPageProvenanceTests {

    private final SpringAiDocumentParser parser = new SpringAiDocumentParser();

    @Test
    void keepsOnePageProvenancePerBlockOfAMultiPagePdf() throws Exception {
        CanonicalDocument document = parse(pdf("Approval policy", "Escalation policy"));

        List<Integer> startPages = document.blocks().stream()
                .map(DocumentBlock::startPage)
                .toList();

        assertEquals(List.of(1, 2), startPages);
    }

    @Test
    void leavesPdfPagesAsProseRatherThanInventingStructure() throws Exception {
        CanonicalDocument document = parse(pdf("Approval policy", "Escalation policy"));

        assertTrue(document.blocks().stream()
                .allMatch(block -> block.kind() == DocumentBlockKind.PARAGRAPH));
    }

    @Test
    void keepsEachPageTextUnderItsOwnPageNumber() throws Exception {
        CanonicalDocument document = parse(pdf("Approval policy", "Escalation policy"));

        DocumentBlock second = document.blocks().get(1);

        assertEquals(2, second.startPage());
        assertTrue(document.text(second).contains("Escalation policy"));
    }

    private CanonicalDocument parse(byte[] bytes) {
        return parser.parse(new DocumentParseRequest(
                        "policy.pdf", "application/octet-stream", bytes, Optional.empty()))
                .document();
    }

    private static byte[] pdf(String... pageTexts) throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(
                            new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
