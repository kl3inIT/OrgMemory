package com.orgmemory.graphrag.chunking;

import com.orgmemory.graphrag.parsing.CanonicalDocument;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Token-measured recursive character splitter with source-span preservation. */
public final class RecursiveCharacterChunker
        implements TextChunker<RecursiveCharacterOptions> {

    public static final ProcessingComponentRef COMPONENT =
            new ProcessingComponentRef("recursive-character", "lightrag-v1.5.4");

    @Override
    public ProcessingComponentRef component() {
        return COMPONENT;
    }

    @Override
    public Class<RecursiveCharacterOptions> optionsType() {
        return RecursiveCharacterOptions.class;
    }

    @Override
    public List<ChunkedText> chunk(
            ChunkingRequest request,
            RecursiveCharacterOptions options) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(options, "options");
        CanonicalDocument document = request.document();
        List<SpanPiece> pieces = splitRecursive(
                document.content(),
                new SpanPiece(0, document.content().length()),
                options.separators(),
                options,
                request.tokenizer());
        List<ChunkedText> result = new ArrayList<>(pieces.size());
        for (SpanPiece piece : pieces) {
            SourceSpan trimmed =
                    ChunkProvenanceFactory.trim(document.content(), piece.start(), piece.end());
            if (trimmed == null) {
                continue;
            }
            String body =
                    document.content().substring(trimmed.startChar(), trimmed.endChar());
            int tokens = request.tokenizer().count(body);
            if (tokens == 0) {
                continue;
            }
            result.add(new ChunkedText(
                    result.size(),
                    body,
                    tokens,
                    null,
                    ChunkProvenanceFactory.create(
                            document, trimmed.startChar(), trimmed.endChar())));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "recursive character chunker produced no non-empty chunks");
        }
        return List.copyOf(result);
    }

    private static List<SpanPiece> splitRecursive(
            String source,
            SpanPiece input,
            List<String> separators,
            RecursiveCharacterOptions options,
            TextTokenizer tokenizer) {
        if (tokenCount(source, input, tokenizer) <= options.chunkTokenSize()) {
            return List.of(input);
        }
        if (separators.isEmpty()) {
            return tokenWindows(source, input, options, tokenizer);
        }

        int separatorIndex = separators.size() - 1;
        String separator = separators.get(separatorIndex);
        for (int index = 0; index < separators.size(); index++) {
            String candidate = separators.get(index);
            if (candidate.isEmpty()
                    || source.indexOf(candidate, input.start()) >= 0
                    && source.indexOf(candidate, input.start()) < input.end()) {
                separatorIndex = index;
                separator = candidate;
                break;
            }
        }
        List<String> remaining = separators.subList(
                Math.min(separatorIndex + 1, separators.size()), separators.size());
        List<SpanPiece> splits = splitBySeparator(
                source, input, separator, options.keepSeparator());
        List<SpanPiece> output = new ArrayList<>();
        List<SpanPiece> good = new ArrayList<>();
        for (SpanPiece split : splits) {
            if (tokenCount(source, split, tokenizer) < options.chunkTokenSize()) {
                good.add(split);
                continue;
            }
            if (!good.isEmpty()) {
                output.addAll(merge(
                        source,
                        good,
                        options.chunkTokenSize(),
                        options.overlapTokenSize(),
                        tokenizer));
                good.clear();
            }
            if (remaining.isEmpty()) {
                output.addAll(tokenWindows(source, split, options, tokenizer));
            } else {
                output.addAll(splitRecursive(
                        source, split, remaining, options, tokenizer));
            }
        }
        if (!good.isEmpty()) {
            output.addAll(merge(
                    source,
                    good,
                    options.chunkTokenSize(),
                    options.overlapTokenSize(),
                    tokenizer));
        }
        return output;
    }

    private static List<SpanPiece> splitBySeparator(
            String source,
            SpanPiece input,
            String separator,
            boolean keepSeparator) {
        if (separator.isEmpty()) {
            List<SpanPiece> characters = new ArrayList<>(input.end() - input.start());
            for (int offset = input.start(); offset < input.end(); offset++) {
                characters.add(new SpanPiece(offset, offset + 1));
            }
            return characters;
        }
        List<Integer> matches = new ArrayList<>();
        int cursor = input.start();
        int match;
        while ((match = source.indexOf(separator, cursor)) >= 0 && match < input.end()) {
            matches.add(match);
            cursor = match + separator.length();
        }
        if (matches.isEmpty()) {
            return List.of(input);
        }
        List<SpanPiece> pieces = new ArrayList<>();
        if (keepSeparator) {
            int first = matches.getFirst();
            if (first > input.start()) {
                pieces.add(new SpanPiece(input.start(), first));
            }
            for (int index = 0; index < matches.size(); index++) {
                int start = matches.get(index);
                int end = index + 1 < matches.size() ? matches.get(index + 1) : input.end();
                if (end > start) {
                    pieces.add(new SpanPiece(start, end));
                }
            }
        } else {
            cursor = input.start();
            for (int separatorStart : matches) {
                if (separatorStart > cursor) {
                    pieces.add(new SpanPiece(cursor, separatorStart));
                }
                cursor = separatorStart + separator.length();
            }
            if (cursor < input.end()) {
                pieces.add(new SpanPiece(cursor, input.end()));
            }
        }
        return pieces;
    }

    private static List<SpanPiece> merge(
            String source,
            List<SpanPiece> splits,
            int chunkSize,
            int overlap,
            TextTokenizer tokenizer) {
        List<SpanPiece> result = new ArrayList<>();
        List<SpanPiece> current = new ArrayList<>();
        for (SpanPiece split : splits) {
            if (!current.isEmpty()) {
                SpanPiece candidate =
                        new SpanPiece(current.getFirst().start(), split.end());
                if (tokenCount(source, candidate, tokenizer) > chunkSize) {
                    result.add(new SpanPiece(
                            current.getFirst().start(), current.getLast().end()));
                    while (!current.isEmpty()) {
                        SpanPiece retained = new SpanPiece(
                                current.getFirst().start(), current.getLast().end());
                        SpanPiece withNext =
                                new SpanPiece(current.getFirst().start(), split.end());
                        if (tokenCount(source, retained, tokenizer) <= overlap
                                && tokenCount(source, withNext, tokenizer) <= chunkSize) {
                            break;
                        }
                        current.removeFirst();
                    }
                }
            }
            current.add(split);
        }
        if (!current.isEmpty()) {
            result.add(new SpanPiece(current.getFirst().start(), current.getLast().end()));
        }
        return result;
    }

    private static List<SpanPiece> tokenWindows(
            String source,
            SpanPiece input,
            RecursiveCharacterOptions options,
            TextTokenizer tokenizer) {
        String body = source.substring(input.start(), input.end());
        EncodedText encoded = tokenizer.encode(body);
        List<SpanPiece> pieces = new ArrayList<>();
        int step = options.chunkTokenSize() - options.overlapTokenSize();
        for (int startToken = 0; startToken < encoded.size(); startToken += step) {
            int endToken = Math.min(startToken + options.chunkTokenSize(), encoded.size());
            SourceSpan local = encoded.sourceSpan(startToken, endToken);
            pieces.add(new SpanPiece(
                    input.start() + local.startChar(),
                    input.start() + local.endChar()));
        }
        return pieces;
    }

    private static int tokenCount(
            String source,
            SpanPiece piece,
            TextTokenizer tokenizer) {
        return tokenizer.count(source.substring(piece.start(), piece.end()));
    }

    private record SpanPiece(int start, int end) {

        private SpanPiece {
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException("recursive split span must be non-empty");
            }
        }
    }
}
