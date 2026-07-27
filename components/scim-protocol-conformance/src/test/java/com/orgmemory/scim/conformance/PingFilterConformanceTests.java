package com.orgmemory.scim.conformance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unboundid.scim2.common.filters.Filter;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class PingFilterConformanceTests {

    private static final int MAX_FILTER_CHARS = 2_048;
    private static final int MAX_FILTER_DEPTH = 8;
    private static final int MAX_FILTER_NODES = 64;

    @Test
    void parsesTheSupportedCorpusAndRejectsInvalidGrammar() {
        for (JsonNode fixture : FixtureSupport.json("/scim/filters.json")) {
            String filter = fixture.get("input").asString();
            if (fixture.get("valid").asBoolean()) {
                assertDoesNotThrow(() -> parseBounded(filter), fixture.get("name").asString());
            } else {
                assertThrows(BadRequestException.class, () -> Filter.fromString(filter),
                        fixture.get("name").asString());
            }
        }
    }

    @Test
    void rejectsDepthNodeAndInputLengthBeforeQueryTranslation() {
        String deep = "userName eq \"a\"";
        for (int index = 0; index < MAX_FILTER_DEPTH + 1; index++) {
            deep = "not (" + deep + ")";
        }
        String tooDeep = deep;
        assertThrows(IllegalArgumentException.class, () -> parseBounded(tooDeep));

        List<String> terms = new ArrayList<>();
        for (int index = 0; index < MAX_FILTER_NODES + 1; index++) {
            terms.add("userName eq \"u" + index + "\"");
        }
        String tooManyNodes = String.join(" or ", terms);
        assertThrows(IllegalArgumentException.class, () -> parseBounded(tooManyNodes));

        String tooLong = "userName eq \"" + "a".repeat(MAX_FILTER_CHARS) + "\"";
        assertThrows(IllegalArgumentException.class, () -> parseBounded(tooLong));
    }

    @Test
    void exposesAnAstInsteadOfReturningRawFilterText() throws Exception {
        Filter parsed = parseBounded("userName sw \"a\" and active eq true");

        assertEquals(2, parsed.getCombinedFilters().size());
        assertEquals("userName", parsed.getCombinedFilters().getFirst().getAttributePath().toString());
        assertEquals("active", parsed.getCombinedFilters().getLast().getAttributePath().toString());
    }

    private static Filter parseBounded(String expression) throws BadRequestException {
        if (expression.length() > MAX_FILTER_CHARS) {
            throw new IllegalArgumentException("filter exceeds character limit");
        }
        Filter parsed = Filter.fromString(expression);
        AstSize size = measure(parsed, 1);
        if (size.depth() > MAX_FILTER_DEPTH || size.nodes() > MAX_FILTER_NODES) {
            throw new IllegalArgumentException("filter exceeds AST limits");
        }
        return parsed;
    }

    private static AstSize measure(Filter filter, int depth) {
        int nodes = 1;
        int maximumDepth = depth;
        List<Filter> children = new ArrayList<>();
        if (filter.isCombiningFilter()) {
            children.addAll(filter.getCombinedFilters());
        } else if (filter.isNotFilter()) {
            children.add(filter.getInvertedFilter());
        } else if (filter.isComplexValueFilter()) {
            children.add(filter.getValueFilter());
        }
        for (Filter child : children) {
            AstSize childSize = measure(child, depth + 1);
            nodes += childSize.nodes();
            maximumDepth = Math.max(maximumDepth, childSize.depth());
        }
        return new AstSize(nodes, maximumDepth);
    }

    private record AstSize(int nodes, int depth) {}
}
