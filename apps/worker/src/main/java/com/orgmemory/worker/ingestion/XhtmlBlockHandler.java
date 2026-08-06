package com.orgmemory.worker.ingestion;

import java.util.ArrayList;
import java.util.List;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Turns Tika's XHTML into typed blocks.
 *
 * <p>Tika exposes real structure in XHTML mode: a spreadsheet arrives as one
 * {@code <h1>} per sheet name followed by a {@code <table>}, a Word document as
 * paragraphs and tables, an HTML export as headings, paragraphs and tables. The
 * flat {@code BodyContentHandler} this replaced discarded all of it.
 *
 * <p>A table block carries one row per line with tab-separated cells, which is
 * the shape {@code ParagraphSemanticChunker} splits row-wise while repeating the
 * header line into every fragment.
 */
final class XhtmlBlockHandler extends DefaultHandler {

    private static final String CELL_SEPARATOR = "\t";
    private static final String ROW_SEPARATOR = "\n";

    private final List<ParsedBlock> blocks = new ArrayList<>();
    private final StringBuilder loose = new StringBuilder();
    private final StringBuilder paragraph = new StringBuilder();
    private final StringBuilder headingText = new StringBuilder();
    private final StringBuilder cell = new StringBuilder();
    private final List<List<String>> rows = new ArrayList<>();
    private List<String> row = new ArrayList<>();

    private int tableDepth;
    private int headingLevel;
    private boolean inParagraph;
    private boolean inCell;
    private boolean headerFromMarkup;
    private boolean firstRowPending = true;

    List<ParsedBlock> blocks() {
        return List.copyOf(blocks);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        String element = name(localName, qName);
        switch (element) {
            case "table" -> {
                if (tableDepth == 0) {
                    flushLoose();
                    flushParagraph();
                    rows.clear();
                    row = new ArrayList<>();
                    headerFromMarkup = false;
                    firstRowPending = true;
                }
                tableDepth++;
            }
            case "tr" -> {
                if (tableDepth == 1) {
                    row = new ArrayList<>();
                }
            }
            case "td", "th" -> {
                if (tableDepth == 1) {
                    inCell = true;
                    cell.setLength(0);
                    if (firstRowPending && "th".equals(element)) {
                        headerFromMarkup = true;
                    }
                }
            }
            case "p", "li", "div" -> {
                if (tableDepth == 0 && !"div".equals(element)) {
                    flushLoose();
                    flushParagraph();
                    inParagraph = true;
                    paragraph.setLength(0);
                } else if (tableDepth == 0) {
                    flushLoose();
                }
            }
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                if (tableDepth == 0) {
                    flushLoose();
                    flushParagraph();
                    headingLevel = element.charAt(1) - '0';
                    headingText.setLength(0);
                }
            }
            case "br" -> append(inCell ? " " : "\n");
            default -> {
                // Inline and container elements contribute only their text.
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        String element = name(localName, qName);
        switch (element) {
            case "table" -> {
                tableDepth--;
                if (tableDepth == 0) {
                    flushTable();
                }
            }
            case "tr" -> {
                if (tableDepth == 1) {
                    if (row.stream().anyMatch(value -> !value.isEmpty())) {
                        rows.add(List.copyOf(row));
                        firstRowPending = false;
                    }
                    row = new ArrayList<>();
                }
            }
            case "td", "th" -> {
                if (tableDepth == 1 && inCell) {
                    row.add(BlockText.cell(cell.toString()));
                    cell.setLength(0);
                    inCell = false;
                }
            }
            case "p", "li" -> flushParagraph();
            case "h1", "h2", "h3", "h4", "h5", "h6" -> flushHeading();
            default -> {
                // Nothing to close.
            }
        }
    }

    @Override
    public void characters(char[] characters, int start, int length) {
        append(new String(characters, start, length));
    }

    @Override
    public void endDocument() {
        flushParagraph();
        flushLoose();
    }

    private void append(String text) {
        if (inCell) {
            cell.append(text);
        } else if (headingLevel > 0) {
            headingText.append(text);
        } else if (inParagraph) {
            paragraph.append(text);
        } else if (tableDepth == 0) {
            loose.append(text);
        }
    }

    private void flushParagraph() {
        if (!inParagraph) {
            return;
        }
        inParagraph = false;
        String text = BlockText.prose(paragraph.toString());
        paragraph.setLength(0);
        if (!text.isBlank()) {
            blocks.add(ParsedBlock.paragraph(text));
        }
    }

    private void flushHeading() {
        if (headingLevel == 0) {
            return;
        }
        String text = BlockText.prose(headingText.toString());
        int level = headingLevel;
        headingLevel = 0;
        headingText.setLength(0);
        if (!text.isBlank()) {
            blocks.add(ParsedBlock.heading(text, level));
        }
    }

    /**
     * Emits text that appeared outside any recognized container. Navigation
     * links in an HTML export arrive this way; keeping them as prose is
     * deliberate, because deciding what is boilerplate is a separate concern.
     */
    private void flushLoose() {
        String text = BlockText.prose(loose.toString());
        loose.setLength(0);
        if (!text.isBlank()) {
            blocks.add(ParsedBlock.paragraph(text));
        }
    }

    private void flushTable() {
        if (rows.isEmpty()) {
            return;
        }
        String text = rows.stream()
                .map(cells -> String.join(CELL_SEPARATOR, cells))
                .reduce((left, right) -> left + ROW_SEPARATOR + right)
                .orElse("");
        rows.clear();
        if (text.isBlank()) {
            return;
        }
        blocks.add(ParsedBlock.table(text, headerFromMarkup));
    }

    private static String name(String localName, String qName) {
        String element = localName == null || localName.isEmpty() ? qName : localName;
        return element == null ? "" : element.toLowerCase(java.util.Locale.ROOT);
    }
}
