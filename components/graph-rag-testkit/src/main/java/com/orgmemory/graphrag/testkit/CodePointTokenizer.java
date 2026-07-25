package com.orgmemory.graphrag.testkit;

import com.orgmemory.graphrag.chunking.EncodedText;
import com.orgmemory.graphrag.chunking.TextTokenizer;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic Unicode code-point tokenizer for golden and adapter conformance tests. */
public final class CodePointTokenizer implements TextTokenizer {

    public static final ProcessingComponentRef COMPONENT =
            new ProcessingComponentRef("test-code-point", "1");

    @Override
    public ProcessingComponentRef component() {
        return COMPONENT;
    }

    @Override
    public EncodedText encode(String canonicalText) {
        String source = Objects.requireNonNull(canonicalText, "canonicalText");
        List<Integer> ids = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        for (int offset = 0; offset < source.length(); ) {
            int codePoint = source.codePointAt(offset);
            int width = Character.charCount(codePoint);
            ids.add(codePoint);
            starts.add(offset);
            ends.add(offset + width);
            offset += width;
        }
        return new EncodedText(
                source,
                ids.stream().mapToInt(Integer::intValue).toArray(),
                starts.stream().mapToInt(Integer::intValue).toArray(),
                ends.stream().mapToInt(Integer::intValue).toArray());
    }
}
