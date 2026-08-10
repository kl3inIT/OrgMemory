package com.orgmemory.worker.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.parsing.CanonicalDocument;
import com.orgmemory.graphrag.parsing.DocumentBlock;
import com.orgmemory.graphrag.parsing.DocumentBlockKind;
import com.orgmemory.graphrag.parsing.DocumentParseRequest;
import com.orgmemory.graphrag.parsing.DocumentParseResult;
import com.orgmemory.integrations.documentparsing.springai.SpringAiDocumentParser;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

/**
 * The parser used to flatten every document into one PARAGRAPH block, which
 * left {@code ParagraphSemanticChunker} unreachable. These pin the structure it
 * now depends on.
 */
class TypedBlockParsingTests {

    private final SpringAiDocumentParser parser = new SpringAiDocumentParser();

    @Test
    void keepsAWordTableAsRowsInsteadOfFlatteningItIntoProse() throws Exception {
        CanonicalDocument document = parse("policy.docx", wordWithTable()).document();

        DocumentBlock table = onlyBlockOfKind(document, DocumentBlockKind.TABLE);
        assertEquals(
                """
                Cấp\tSố ngày
                Nhân viên\t60
                Quản lý\t90""",
                document.text(table));
    }

    @Test
    void giveseveryRowOfATableItsOwnLineSoTheHeaderCanBeRepeated() throws Exception {
        CanonicalDocument document = parse("policy.docx", wordWithTable()).document();

        DocumentBlock table = onlyBlockOfKind(document, DocumentBlockKind.TABLE);
        List<String> lines = List.of(document.text(table).split("\n"));

        assertEquals(3, lines.size());
        assertEquals("Cấp\tSố ngày", lines.getFirst());
    }

    @Test
    void keepsProseAndTableAsSeparateBlocks() throws Exception {
        CanonicalDocument document = parse("policy.docx", wordWithTable()).document();

        List<DocumentBlockKind> kinds = document.blocks().stream()
                .map(DocumentBlock::kind)
                .toList();

        assertTrue(kinds.contains(DocumentBlockKind.PARAGRAPH));
        assertTrue(kinds.contains(DocumentBlockKind.TABLE));
    }

    @Test
    void makesADocumentWithATableStructuredSoTheParagraphChunkerCanRun() throws Exception {
        CanonicalDocument document = parse("policy.docx", wordWithTable()).document();

        assertTrue(document.hasStructuredBlocks());
    }

    @Test
    void recordsThatAWordTableHeaderCameFromItsFirstRowRatherThanMarkup() throws Exception {
        CanonicalDocument document = parse("policy.docx", wordWithTable()).document();

        DocumentBlock table = onlyBlockOfKind(document, DocumentBlockKind.TABLE);

        assertEquals("first-row", table.attributes().get("header"));
    }

    @Test
    void leavesAPlainTextFileAsOneUnstructuredBlock() throws Exception {
        DocumentParseResult parsed = parse(
                "notes.txt",
                "First step.\r\n\r\n\r\nSecond\tstep.".getBytes(StandardCharsets.UTF_8));

        assertEquals(1, parsed.document().blocks().size());
        assertEquals("First step.\n\nSecond step.", parsed.document().content());
        assertFalse(parsed.document().hasStructuredBlocks());
    }

    @Test
    void keepsEveryBlockSpanInsideTheCanonicalTextItDescribes() throws Exception {
        CanonicalDocument document = parse("policy.docx", wordWithTable()).document();

        for (DocumentBlock block : document.blocks()) {
            assertFalse(document.text(block).isBlank());
        }
    }

    private static DocumentBlock onlyBlockOfKind(
            CanonicalDocument document, DocumentBlockKind kind) {
        List<DocumentBlock> matching = document.blocks().stream()
                .filter(block -> block.kind() == kind)
                .toList();
        assertEquals(1, matching.size(), "expected exactly one " + kind + " block");
        return matching.getFirst();
    }

    private DocumentParseResult parse(String fileName, byte[] bytes) {
        return parser.parse(new DocumentParseRequest(
                fileName, "application/octet-stream", bytes, Optional.empty()));
    }

    private static byte[] wordWithTable() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Thời gian thử việc là 60 ngày.");
            var table = document.createTable(3, 2);
            String[][] cells = {
                {"Cấp", "Số ngày"},
                {"Nhân viên", "60"},
                {"Quản lý", "90"}
            };
            for (int r = 0; r < cells.length; r++) {
                for (int c = 0; c < cells[r].length; c++) {
                    table.getRow(r).getCell(c).setText(cells[r][c]);
                }
            }
            document.write(out);
            return out.toByteArray();
        }
    }
}
