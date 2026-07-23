package com.orgmemory.integrations.graphrag.springai;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.IntArrayList;
import com.orgmemory.graphrag.chunking.EncodedText;
import com.orgmemory.graphrag.chunking.SourceSpan;
import com.orgmemory.graphrag.chunking.TextTokenizer;
import com.orgmemory.graphrag.chunking.TokenRangeLocator;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/** JTokkit tokenizer with LightRAG-style anchored token-window source mapping. */
public final class JtokkitTextTokenizer implements TextTokenizer {

    private static final int SOURCE_SEARCH_RADIUS = 32;
    private final Encoding encoding;
    private final ProcessingComponentRef component;

    public JtokkitTextTokenizer(String encodingName) {
        String normalized = Objects.requireNonNull(encodingName, "encodingName").trim();
        this.encoding = Encodings.newLazyEncodingRegistry()
                .getEncoding(normalized)
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown JTokkit encoding " + normalized));
        this.component = new ProcessingComponentRef(
                "jtokkit-" + normalized.replace('_', '-'),
                "1.1.0");
    }

    @Override
    public ProcessingComponentRef component() {
        return component;
    }

    @Override
    public EncodedText encode(String canonicalText) {
        String source = Objects.requireNonNull(canonicalText, "canonicalText");
        int[] tokenIds = encoding.encodeOrdinary(source).toArray();
        return new EncodedText(source, tokenIds, new AnchoredRangeLocator(encoding));
    }

    @Override
    public int count(String canonicalText) {
        return encoding.countTokensOrdinary(Objects.requireNonNull(canonicalText, "canonicalText"));
    }

    private static final class AnchoredRangeLocator implements TokenRangeLocator {

        private final Encoding encoding;
        private final NavigableMap<Integer, Integer> anchors = new TreeMap<>(Map.of(0, 0));

        private AnchoredRangeLocator(Encoding encoding) {
            this.encoding = encoding;
        }

        @Override
        public synchronized SourceSpan locate(
                String source,
                int[] tokenIds,
                int fromInclusive,
                int toExclusive) {
            Map.Entry<Integer, Integer> anchor = anchors.floorEntry(fromInclusive);
            int predictedStart = anchor.getValue()
                    + decode(tokenIds, anchor.getKey(), fromInclusive).length();
            String window = decode(tokenIds, fromInclusive, toExclusive);
            if (window.indexOf('\uFFFD') >= 0) {
                throw new TokenSourceMappingException(
                        "token window crosses an undecodable UTF-8 boundary");
            }
            int predictedEnd = predictedStart + window.length();
            int start = predictedStart;
            if (predictedEnd > source.length()
                    || !source.regionMatches(predictedStart, window, 0, window.length())) {
                int lower = Math.max(0, predictedStart - SOURCE_SEARCH_RADIUS);
                int upper = Math.min(
                        source.length(),
                        predictedEnd + SOURCE_SEARCH_RADIUS + window.length());
                int found = source.indexOf(window, lower);
                if (found < 0 || found + window.length() > upper) {
                    throw new TokenSourceMappingException(
                            "decoded token window cannot be mapped to canonical text");
                }
                start = found;
            }
            anchors.put(fromInclusive, start);
            return new SourceSpan(start, start + window.length());
        }

        private String decode(int[] ids, int fromInclusive, int toExclusive) {
            IntArrayList values = new IntArrayList(toExclusive - fromInclusive);
            for (int index = fromInclusive; index < toExclusive; index++) {
                values.add(ids[index]);
            }
            return encoding.decode(values);
        }
    }
}
