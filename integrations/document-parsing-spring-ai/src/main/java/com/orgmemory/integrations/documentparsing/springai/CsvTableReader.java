package com.orgmemory.integrations.documentparsing.springai;

import com.orgmemory.graphrag.parsing.DocumentParseException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small RFC-4180-style reader with delimiter sniffing for organizational exports. */
final class CsvTableReader {

    private static final List<Character> CANDIDATES = List.of(',', ';', '\t', '|');

    private CsvTableReader() {
    }

    static ParsedBlock read(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\ufeff') {
            text = text.substring(1);
        }
        Candidate best = null;
        for (char delimiter : CANDIDATES) {
            List<List<String>> rows = parse(text, delimiter);
            Candidate candidate = new Candidate(rows, score(rows));
            if (best == null || candidate.score() > best.score()) {
                best = candidate;
            }
        }
        if (best == null || best.rows().isEmpty()) {
            throw new DocumentParseException("NO_EXTRACTABLE_TEXT", "No extractable text was found");
        }
        String table = best.rows().stream()
                .map(row -> row.stream().map(BlockText::cell).toList())
                .filter(row -> row.stream().anyMatch(cell -> !cell.isBlank()))
                .map(row -> String.join("\t", row))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        if (table.isBlank()) {
            throw new DocumentParseException("NO_EXTRACTABLE_TEXT", "No extractable text was found");
        }
        return ParsedBlock.table(table, false);
    }

    private static List<List<String>> parse(String text, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else if (quoted) {
                    quoted = false;
                } else if (!fieldStarted) {
                    quoted = true;
                    fieldStarted = true;
                } else {
                    field.append(current);
                }
                continue;
            }
            if (!quoted && current == delimiter) {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                continue;
            }
            if (!quoted && (current == '\r' || current == '\n')) {
                if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
                row.add(field.toString());
                addRow(rows, row);
                row = new ArrayList<>();
                field.setLength(0);
                fieldStarted = false;
                continue;
            }
            field.append(current);
            fieldStarted = true;
        }
        if (quoted) {
            throw new DocumentParseException("MALFORMED_CSV", "The CSV file contains an unclosed quoted field");
        }
        if (fieldStarted || !row.isEmpty()) {
            row.add(field.toString());
            addRow(rows, row);
        }
        return List.copyOf(rows);
    }

    private static void addRow(List<List<String>> rows, List<String> row) {
        if (row.stream().anyMatch(value -> !value.isBlank())) {
            rows.add(List.copyOf(row));
        }
    }

    private static int score(List<List<String>> rows) {
        if (rows.isEmpty()) {
            return Integer.MIN_VALUE;
        }
        Map<Integer, Integer> widths = new HashMap<>();
        rows.stream().limit(20).forEach(row -> widths.merge(row.size(), 1, Integer::sum));
        var dominant = widths.entrySet().stream()
                .filter(entry -> entry.getKey() > 1)
                .max(Map.Entry.<Integer, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .orElse(null);
        return dominant == null ? 0 : dominant.getValue() * 100 + dominant.getKey();
    }

    private record Candidate(List<List<String>> rows, int score) {
    }
}
