package com.orgmemory.worker.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.parsing.DocumentBlockKind;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.parsers.SAXParserFactory;
import org.junit.jupiter.api.Test;

/**
 * The fixtures here are the XHTML Tika actually produced for a spreadsheet and
 * an HTML export, captured while measuring what its XHTML mode exposes. Driving
 * the handler directly covers formats the upload allowlist has not admitted yet.
 */
class XhtmlBlockHandlerTests {

    private static final String SPREADSHEET = """
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title></title></head>
            <body><div class="sheet"><h1>Lương</h1>
            <table><tbody><tr>\t<td>Phòng ban</td>\t<td>Chức danh</td>\t<td>Lương cơ bản</td></tr>
            <tr>\t<td>Kỹ thuật</td>\t<td>Kỹ sư</td>\t<td>25000000</td></tr>
            <tr>\t<td>Nhân sự</td>\t<td>Chuyên viên</td>\t<td>18000000</td></tr>
            </tbody></table>
            </div>
            <div class="sheet"><h1>Nhân sự</h1>
            <table><tbody><tr>\t<td>Tổng</td>\t<td>128</td></tr>
            </tbody></table>
            </div>
            </body></html>
            """;

    private static final String WIKI_EXPORT = """
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Quy chế</title></head>
            <body>
            <a href="/a">Trang chủ</a><a href="/b">Liên hệ</a>
            <h1>Quy chế nhân sự</h1>
            <h2>Thử việc</h2>
            <p>Thời gian thử việc là 60 ngày.</p>
            <table><tbody><tr>\t<th>Cấp</th>\t<th>Số ngày</th></tr>
            <tr>\t<td>Nhân viên</td>\t<td>60</td></tr>
            <tr>\t<td>Quản lý</td>\t<td>90</td></tr>
            </tbody></table>
            </body></html>
            """;

    @Test
    void turnsEachSpreadsheetSheetIntoAHeadingFollowedByItsTable() throws Exception {
        List<ParsedBlock> blocks = handle(SPREADSHEET);

        assertEquals(
                List.of(
                        DocumentBlockKind.HEADING,
                        DocumentBlockKind.TABLE,
                        DocumentBlockKind.HEADING,
                        DocumentBlockKind.TABLE),
                blocks.stream().map(ParsedBlock::kind).toList());
        assertEquals("Lương", blocks.getFirst().text());
        assertEquals(1, blocks.getFirst().headingLevel());
    }

    @Test
    void keepsEverySpreadsheetCellBoundaryAsATab() throws Exception {
        List<ParsedBlock> blocks = handle(SPREADSHEET);

        assertEquals(
                """
                Phòng ban\tChức danh\tLương cơ bản
                Kỹ thuật\tKỹ sư\t25000000
                Nhân sự\tChuyên viên\t18000000""",
                blocks.get(1).text());
    }

    @Test
    void recordsThatAWikiExportHeaderCameFromTableHeaderMarkup() throws Exception {
        ParsedBlock table = handle(WIKI_EXPORT).stream()
                .filter(block -> block.kind() == DocumentBlockKind.TABLE)
                .findFirst()
                .orElseThrow();

        assertEquals("markup", table.attributes().get("header"));
        assertEquals("Cấp\tSố ngày", table.text().lines().findFirst().orElseThrow());
    }

    @Test
    void keepsTheHeadingLevelsOfAWikiExport() throws Exception {
        List<ParsedBlock> headings = handle(WIKI_EXPORT).stream()
                .filter(block -> block.kind() == DocumentBlockKind.HEADING)
                .toList();

        assertEquals(List.of("Quy chế nhân sự", "Thử việc"), headings.stream()
                .map(ParsedBlock::text)
                .toList());
        assertEquals(List.of(1, 2), headings.stream()
                .map(ParsedBlock::headingLevel)
                .toList());
    }

    @Test
    void keepsNavigationTextAsProseRatherThanDiscardingItSilently() throws Exception {
        List<ParsedBlock> blocks = handle(WIKI_EXPORT);

        assertTrue(
                blocks.stream()
                        .filter(block -> block.kind() == DocumentBlockKind.PARAGRAPH)
                        .anyMatch(block -> block.text().contains("Trang chủ")),
                "boilerplate removal is a later decision, so nothing may vanish here");
    }

    @Test
    void doesNotLeakTheWhitespaceBetweenCellsIntoABlockOfItsOwn() throws Exception {
        List<ParsedBlock> blocks = handle(SPREADSHEET);

        assertTrue(blocks.stream().noneMatch(block -> block.text().isBlank()));
    }

    private static List<ParsedBlock> handle(String xhtml) throws Exception {
        XhtmlBlockHandler handler = new XhtmlBlockHandler();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.newSAXParser().parse(
                new ByteArrayInputStream(xhtml.getBytes(StandardCharsets.UTF_8)), handler);
        return handler.blocks();
    }
}
