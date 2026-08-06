package com.orgmemory.worker.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.chunking.ChunkedText;
import com.orgmemory.graphrag.chunking.ChunkingRequest;
import com.orgmemory.graphrag.chunking.ParagraphSemanticChunker;
import com.orgmemory.graphrag.chunking.ParagraphSemanticOptions;
import com.orgmemory.graphrag.parsing.CanonicalDocument;
import com.orgmemory.graphrag.parsing.DocumentParseRequest;
import com.orgmemory.integrations.graphrag.springai.JtokkitTextTokenizer;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

/**
 * {@code ParagraphSemanticChunker} has carried row-wise table splitting with
 * header repetition since it was ported, and has never been able to run: the
 * parser emitted only PARAGRAPH blocks, so no table ever reached it. This is the
 * end-to-end proof that a parsed document now does.
 */
class ParsedTableReachesTheParagraphChunkerTests {

    private static final String HEADER = "Phòng ban\tChức danh\tLương cơ bản";

    private final SpringAiDocumentParser parser = new SpringAiDocumentParser();
    private final ParagraphSemanticChunker chunker = new ParagraphSemanticChunker();

    @Test
    void repeatsTheHeaderRowIntoEveryFragmentOfALongTable() throws Exception {
        List<ChunkedText> chunks = chunk(parse(salaryTable(40)), 60);

        assertTrue(chunks.size() > 1, "a 40-row table should not fit one 60-token chunk");
        for (ChunkedText chunk : chunks) {
            assertEquals(
                    HEADER,
                    chunk.content().lines().findFirst().orElseThrow(),
                    "every fragment must carry the header so its numbers stay readable");
        }
    }

    @Test
    void doesNotSplitARowAcrossTwoFragments() throws Exception {
        List<ChunkedText> chunks = chunk(parse(salaryTable(40)), 60);

        for (ChunkedText chunk : chunks) {
            chunk.content().lines().skip(1).forEach(line ->
                    assertEquals(
                            2,
                            line.chars().filter(character -> character == '\t').count(),
                            "row lost a cell boundary: " + line));
        }
    }

    @Test
    void keepsEveryDataRowExactlyOnce() throws Exception {
        List<ChunkedText> chunks = chunk(parse(salaryTable(40)), 60);

        List<String> rows = chunks.stream()
                .flatMap(chunk -> chunk.content().lines().skip(1))
                .filter(line -> !line.isBlank())
                .toList();

        assertEquals(40, rows.size());
        assertEquals(40, rows.stream().distinct().count());
    }

    private List<ChunkedText> chunk(CanonicalDocument document, int chunkTokenSize) {
        return chunker.chunk(
                new ChunkingRequest(
                        document, new JtokkitTextTokenizer("cl100k_base"), Optional.empty()),
                new ParagraphSemanticOptions(chunkTokenSize, 0, 120, false, List.of()));
    }

    private CanonicalDocument parse(byte[] bytes) {
        return parser.parse(new DocumentParseRequest(
                        "salaries.docx", "application/octet-stream", bytes, Optional.empty()))
                .document();
    }

    private static byte[] salaryTable(int dataRows) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var table = document.createTable(dataRows + 1, 3);
            table.getRow(0).getCell(0).setText("Phòng ban");
            table.getRow(0).getCell(1).setText("Chức danh");
            table.getRow(0).getCell(2).setText("Lương cơ bản");
            for (int index = 1; index <= dataRows; index++) {
                table.getRow(index).getCell(0).setText("Phòng " + index);
                table.getRow(index).getCell(1).setText("Chức danh " + index);
                table.getRow(index).getCell(2).setText(String.valueOf(10000000 + index));
            }
            document.write(out);
            return out.toByteArray();
        }
    }
}
