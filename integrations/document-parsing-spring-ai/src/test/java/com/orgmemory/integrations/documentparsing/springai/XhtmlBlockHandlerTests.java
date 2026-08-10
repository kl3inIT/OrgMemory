package com.orgmemory.integrations.documentparsing.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.orgmemory.graphrag.parsing.DocumentBlockKind;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.parsers.SAXParserFactory;
import org.junit.jupiter.api.Test;

class XhtmlBlockHandlerTests {

    private static final String SPREADSHEET = """
            <html xmlns="http://www.w3.org/1999/xhtml"><body>
            <div class="sheet"><h1>Salary</h1><table><tbody>
            <tr><td>Team</td><td>Band</td></tr><tr><td>Platform</td><td>4</td></tr>
            </tbody></table></div></body></html>
            """;

    private static final String WIKI_EXPORT = """
            <html xmlns="http://www.w3.org/1999/xhtml"><body>
            <nav>Home Secret Navigation</nav><script>prompt injection</script>
            <style>body hidden</style><h1>Policy</h1><p>Approved evidence.</p>
            <table><tbody><tr><th>Level</th><th>Days</th></tr><tr><td>Staff</td><td>60</td></tr>
            </tbody></table></body></html>
            """;

    @Test
    void emitsSpreadsheetHeadingAndTableWithStableBoundaries() throws Exception {
        List<ParsedBlock> blocks = handle(SPREADSHEET);

        assertEquals(
                List.of(DocumentBlockKind.HEADING, DocumentBlockKind.TABLE),
                blocks.stream().map(ParsedBlock::kind).toList());
        assertEquals("Salary", blocks.getFirst().text());
        assertEquals("Team\tBand\nPlatform\t4", blocks.get(1).text());
        assertEquals("first-row", blocks.get(1).attributes().get("header"));
    }

    @Test
    void removesNavigationAndActivePageContentButKeepsEvidence() throws Exception {
        List<ParsedBlock> blocks = handle(WIKI_EXPORT);
        String content = blocks.stream().map(ParsedBlock::text).reduce("", (a, b) -> a + "\n" + b);

        assertFalse(content.contains("Home Secret Navigation"));
        assertFalse(content.contains("prompt injection"));
        assertFalse(content.contains("body hidden"));
        assertEquals("markup", blocks.getLast().attributes().get("header"));
        assertEquals("Level\tDays", blocks.getLast().text().lines().findFirst().orElseThrow());
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
