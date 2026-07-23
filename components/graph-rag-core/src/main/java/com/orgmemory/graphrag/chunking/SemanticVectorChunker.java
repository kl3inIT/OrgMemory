package com.orgmemory.graphrag.chunking;

import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.parsing.CanonicalDocument;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** LightRAG semantic-vector sentence grouping with deterministic breakpoint selection. */
public final class SemanticVectorChunker implements TextChunker<SemanticVectorOptions> {

    public static final ProcessingComponentRef COMPONENT =
            new ProcessingComponentRef("semantic-vector", "lightrag-v1.5.4");

    @Override
    public ProcessingComponentRef component() {
        return COMPONENT;
    }

    @Override
    public Class<SemanticVectorOptions> optionsType() {
        return SemanticVectorOptions.class;
    }

    @Override
    public List<ChunkedText> chunk(
            ChunkingRequest request,
            SemanticVectorOptions options) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(options, "options");
        TextEmbeddingPort embeddings = request.semanticEmbedding().orElseThrow(
                () -> new SemanticEmbeddingUnavailableException(
                        "semantic-vector requires a configured embedding port"));
        CanonicalDocument document = request.document();
        List<Sentence> sentences =
                sentences(document.content(), Pattern.compile(options.sentenceSplitRegex()));
        if (sentences.size() == 1) {
            return hardSplit(request, options, sentences.getFirst().start(), sentences.getFirst().end());
        }

        List<String> windows = combinedWindows(document.content(), sentences, options.bufferSize());
        List<FloatVector> vectors = embeddings.embedAll(windows);
        requireOrderedEmbeddings(windows, vectors);
        double[] distances = adjacentCosineDistances(vectors);
        double[] breakpointValues = options.threshold()
                        == SemanticVectorOptions.BreakpointThreshold.GRADIENT
                ? gradient(distances)
                : distances;
        double threshold = threshold(breakpointValues, options);
        List<Integer> breakpoints = new ArrayList<>();
        for (int index = 0; index < breakpointValues.length; index++) {
            if (breakpointValues[index] > threshold) {
                breakpoints.add(index);
            }
        }

        List<ChunkedText> chunks = new ArrayList<>();
        int sentenceStart = 0;
        for (int breakpoint : breakpoints) {
            appendGroup(
                    request,
                    options,
                    sentences,
                    sentenceStart,
                    breakpoint + 1,
                    chunks);
            sentenceStart = breakpoint + 1;
        }
        appendGroup(request, options, sentences, sentenceStart, sentences.size(), chunks);
        return List.copyOf(chunks);
    }

    private static void appendGroup(
            ChunkingRequest request,
            SemanticVectorOptions options,
            List<Sentence> sentences,
            int fromSentence,
            int toSentence,
            List<ChunkedText> output) {
        if (fromSentence >= toSentence) {
            return;
        }
        int start = sentences.get(fromSentence).start();
        int end = sentences.get(toSentence - 1).end();
        String content = sentences.subList(fromSentence, toSentence).stream()
                .map(sentence -> request.document()
                        .content()
                        .substring(sentence.start(), sentence.end())
                        .strip())
                .filter(value -> !value.isEmpty())
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        int tokens = request.tokenizer().count(content);
        if (tokens <= options.chunkTokenSize()) {
            output.add(new ChunkedText(
                    output.size(),
                    content,
                    tokens,
                    null,
                    ChunkProvenanceFactory.create(request.document(), start, end)));
            return;
        }
        List<ChunkedText> split = hardSplit(request, options, start, end);
        for (ChunkedText chunk : split) {
            output.add(new ChunkedText(
                    output.size(),
                    chunk.content(),
                    chunk.tokenCount(),
                    chunk.heading(),
                    chunk.provenance()));
        }
    }

    private static List<ChunkedText> hardSplit(
            ChunkingRequest request,
            SemanticVectorOptions options,
            int start,
            int end) {
        String source = request.document().content().substring(start, end);
        CanonicalDocument local = CanonicalDocument.text(source);
        RecursiveCharacterOptions recursive = new RecursiveCharacterOptions(
                options.chunkTokenSize(),
                0,
                RecursiveCharacterOptions.DEFAULT_SEPARATORS,
                true);
        List<ChunkedText> localChunks = new RecursiveCharacterChunker().chunk(
                new ChunkingRequest(local, request.tokenizer(), java.util.Optional.empty()),
                recursive);
        List<ChunkedText> result = new ArrayList<>(localChunks.size());
        for (ChunkedText chunk : localChunks) {
            int globalStart = start + chunk.provenance().startChar();
            int globalEnd = start + chunk.provenance().endChar();
            result.add(new ChunkedText(
                    result.size(),
                    chunk.content(),
                    chunk.tokenCount(),
                    null,
                    ChunkProvenanceFactory.create(
                            request.document(), globalStart, globalEnd)));
        }
        return result;
    }

    private static List<Sentence> sentences(String source, Pattern splitPattern) {
        List<Sentence> result = new ArrayList<>();
        Matcher matcher = splitPattern.matcher(source);
        int cursor = 0;
        while (matcher.find()) {
            addSentence(source, cursor, matcher.start(), result);
            cursor = matcher.end();
        }
        addSentence(source, cursor, source.length(), result);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("semantic-vector found no sentences");
        }
        return result;
    }

    private static void addSentence(
            String source,
            int start,
            int end,
            List<Sentence> target) {
        SourceSpan trimmed = ChunkProvenanceFactory.trim(source, start, end);
        if (trimmed != null) {
            target.add(new Sentence(trimmed.startChar(), trimmed.endChar()));
        }
    }

    private static List<String> combinedWindows(
            String source,
            List<Sentence> sentences,
            int buffer) {
        List<String> result = new ArrayList<>(sentences.size());
        for (int index = 0; index < sentences.size(); index++) {
            int from = Math.max(0, index - buffer);
            int to = Math.min(sentences.size(), index + buffer + 1);
            result.add(sentences.subList(from, to).stream()
                    .map(sentence -> source.substring(sentence.start(), sentence.end()).strip())
                    .reduce((left, right) -> left + " " + right)
                    .orElse(""));
        }
        return List.copyOf(result);
    }

    private static void requireOrderedEmbeddings(
            List<String> inputs,
            List<FloatVector> vectors) {
        Objects.requireNonNull(vectors, "embedding vectors");
        if (vectors.size() != inputs.size()) {
            throw new IllegalStateException(
                    "embedding response count does not match semantic sentence window count");
        }
        Integer dimensions = null;
        for (FloatVector vector : vectors) {
            Objects.requireNonNull(vector, "embedding vector");
            if (dimensions == null) {
                dimensions = vector.dimensions();
            } else if (dimensions != vector.dimensions()) {
                throw new IllegalStateException(
                        "semantic embedding response contains mixed dimensions");
            }
        }
    }

    private static double[] adjacentCosineDistances(List<FloatVector> vectors) {
        double[] result = new double[vectors.size() - 1];
        for (int index = 0; index < result.length; index++) {
            result[index] = 1.0 - cosine(vectors.get(index), vectors.get(index + 1));
        }
        return result;
    }

    private static double cosine(FloatVector left, FloatVector right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.dimensions(); index++) {
            double leftValue = left.valueAt(index);
            double rightValue = right.valueAt(index);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static double[] gradient(double[] values) {
        if (values.length < 2) {
            return values.clone();
        }
        double[] result = new double[values.length];
        result[0] = values[1] - values[0];
        for (int index = 1; index < values.length - 1; index++) {
            result[index] = (values[index + 1] - values[index - 1]) / 2.0;
        }
        result[result.length - 1] = values[values.length - 1] - values[values.length - 2];
        return result;
    }

    private static double threshold(
            double[] values,
            SemanticVectorOptions options) {
        if (values.length == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return switch (options.threshold()) {
            case PERCENTILE, GRADIENT -> percentile(values, options.thresholdAmount());
            case STANDARD_DEVIATION ->
                    mean(values) + options.thresholdAmount() * standardDeviation(values);
            case INTERQUARTILE -> {
                double q1 = percentile(values, 25);
                double q3 = percentile(values, 75);
                yield q3 + options.thresholdAmount() * (q3 - q1);
            }
        };
    }

    private static double percentile(double[] source, double percentile) {
        if (percentile < 0 || percentile > 100) {
            throw new IllegalArgumentException("percentile threshold must be between 0 and 100");
        }
        double[] values = source.clone();
        Arrays.sort(values);
        if (values.length == 1) {
            return values[0];
        }
        double position = percentile / 100.0 * (values.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return values[lower];
        }
        double fraction = position - lower;
        return values[lower] + fraction * (values[upper] - values[lower]);
    }

    private static double mean(double[] values) {
        return Arrays.stream(values).average().orElse(0);
    }

    private static double standardDeviation(double[] values) {
        double average = mean(values);
        double variance = Arrays.stream(values)
                .map(value -> {
                    double delta = value - average;
                    return delta * delta;
                })
                .average()
                .orElse(0);
        return Math.sqrt(variance);
    }

    private record Sentence(int start, int end) {
    }
}
