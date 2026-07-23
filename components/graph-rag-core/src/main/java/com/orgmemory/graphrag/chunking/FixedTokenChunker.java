package com.orgmemory.graphrag.chunking;

import com.orgmemory.graphrag.parsing.CanonicalDocument;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** LightRAG fixed-token window strategy, including delimiter-only compatibility mode. */
public final class FixedTokenChunker implements TextChunker<FixedTokenOptions> {

    public static final ProcessingComponentRef COMPONENT =
            new ProcessingComponentRef("fixed-token", "lightrag-v1.5.4");

    @Override
    public ProcessingComponentRef component() {
        return COMPONENT;
    }

    @Override
    public Class<FixedTokenOptions> optionsType() {
        return FixedTokenOptions.class;
    }

    @Override
    public List<ChunkedText> chunk(ChunkingRequest request, FixedTokenOptions options) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(options, "options");
        CanonicalDocument document = request.document();
        List<SourceSpan> segments = options.splitBy() == null
                ? List.of(new SourceSpan(0, document.content().length()))
                : splitSpans(document.content(), options.splitBy());
        List<ChunkedText> result = new ArrayList<>();
        for (SourceSpan segment : segments) {
            SourceSpan trimmed =
                    ChunkProvenanceFactory.trim(document.content(), segment.startChar(), segment.endChar());
            if (trimmed == null) {
                continue;
            }
            String segmentText =
                    document.content().substring(trimmed.startChar(), trimmed.endChar());
            EncodedText encoded = request.tokenizer().encode(segmentText);
            if (encoded.size() == 0) {
                continue;
            }
            if (options.splitOnly()) {
                if (encoded.size() > options.chunkTokenSize()) {
                    throw new ChunkTokenLimitExceededException(
                            encoded.size(), options.chunkTokenSize());
                }
                result.add(toChunk(
                        result.size(),
                        document,
                        trimmed.startChar(),
                        trimmed.endChar(),
                        encoded.size()));
                continue;
            }
            int step = options.chunkTokenSize() - options.overlapTokenSize();
            for (int tokenStart = 0; tokenStart < encoded.size(); tokenStart += step) {
                int tokenEnd = Math.min(tokenStart + options.chunkTokenSize(), encoded.size());
                SourceSpan output = null;
                int actualTokenCount = Integer.MAX_VALUE;
                while (tokenEnd > tokenStart) {
                    SourceSpan local = encoded.sourceSpan(tokenStart, tokenEnd);
                    int sourceStart = trimmed.startChar() + local.startChar();
                    int sourceEnd = trimmed.startChar() + local.endChar();
                    output = ChunkProvenanceFactory.trim(
                            document.content(), sourceStart, sourceEnd);
                    if (output == null) {
                        break;
                    }
                    actualTokenCount = request.tokenizer().count(
                            document.content().substring(
                                    output.startChar(), output.endChar()));
                    if (actualTokenCount <= options.chunkTokenSize()) {
                        break;
                    }
                    tokenEnd--;
                }
                if (output != null) {
                    if (actualTokenCount > options.chunkTokenSize()) {
                        throw new ChunkTokenLimitExceededException(
                                actualTokenCount, options.chunkTokenSize());
                    }
                    result.add(toChunk(
                            result.size(),
                            document,
                            output.startChar(),
                            output.endChar(),
                            actualTokenCount));
                }
            }
        }
        return List.copyOf(result);
    }

    private static ChunkedText toChunk(
            int order,
            CanonicalDocument document,
            int startChar,
            int endChar,
            int tokenCount) {
        return new ChunkedText(
                order,
                document.content().substring(startChar, endChar),
                tokenCount,
                null,
                ChunkProvenanceFactory.create(document, startChar, endChar));
    }

    private static List<SourceSpan> splitSpans(String source, String delimiter) {
        List<SourceSpan> spans = new ArrayList<>();
        int cursor = 0;
        int match;
        while ((match = source.indexOf(delimiter, cursor)) >= 0) {
            if (match > cursor) {
                spans.add(new SourceSpan(cursor, match));
            }
            cursor = match + delimiter.length();
        }
        if (cursor < source.length()) {
            spans.add(new SourceSpan(cursor, source.length()));
        }
        return spans;
    }
}
