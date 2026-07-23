package com.orgmemory.graphrag.extraction;

import com.orgmemory.graphrag.chunking.EncodedText;
import com.orgmemory.graphrag.chunking.SourceSpan;
import com.orgmemory.graphrag.chunking.TextTokenizer;
import com.orgmemory.graphrag.model.ExtractedEntity;
import com.orgmemory.graphrag.model.ExtractedRelation;
import com.orgmemory.graphrag.model.ExtractionDiagnostics;
import com.orgmemory.graphrag.model.ExtractionRequest;
import com.orgmemory.graphrag.model.ExtractionResult;
import com.orgmemory.graphrag.model.ExtractionRoundMetrics;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.port.EntityRelationExtractor;
import com.orgmemory.graphrag.port.ExtractionModel;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Framework-neutral LightRAG extraction orchestration.
 *
 * <p>The model adapter performs effects and parsing. This class owns prompts,
 * gleaning policy, token guards, deterministic round merging, and final
 * endpoint validation.
 */
public final class LightRagEntityRelationExtractor implements EntityRelationExtractor {

    private final ExtractionModel model;
    private final TextTokenizer tokenizer;

    public LightRagEntityRelationExtractor(
            ExtractionModel model,
            TextTokenizer tokenizer) {
        this.model = Objects.requireNonNull(model, "model");
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer");
    }

    @Override
    public ExtractionResult extract(ExtractionRequest request) {
        Objects.requireNonNull(request, "request");
        LightRagExtractionPrompt.RenderedPrompt prompt =
                LightRagExtractionPrompt.render(
                        request,
                        boundedSectionContext(request));
        List<ExtractionRoundMetrics> metrics = new ArrayList<>(2);
        List<ExtractionConversationMessage> initialConversation = List.of(
                new ExtractionConversationMessage(
                        ExtractionConversationMessage.Role.USER,
                        prompt.initialUserInstruction()));
        ExtractionRoundResponse initial = invoke(
                request,
                0,
                prompt.systemInstruction(),
                initialConversation,
                metrics);
        validateRoundLimits(request, initial);

        ExtractionRoundResponse glean = null;
        ExtractionDiagnostics.GleaningOutcome gleaningOutcome =
                ExtractionDiagnostics.GleaningOutcome.DISABLED;
        if (request.profile().maxGleaningRounds() > 0) {
            List<ExtractionConversationMessage> gleanConversation = List.of(
                    initialConversation.getFirst(),
                    new ExtractionConversationMessage(
                            ExtractionConversationMessage.Role.ASSISTANT,
                            initial.assistantMessage()),
                    new ExtractionConversationMessage(
                            ExtractionConversationMessage.Role.USER,
                            prompt.continuationUserInstruction()));
            int gleaningInputTokens =
                    tokenCount(prompt.systemInstruction(), gleanConversation);
            if (request.profile().maxGleaningInputTokens() > 0
                    && gleaningInputTokens
                            > request.profile().maxGleaningInputTokens()) {
                gleaningOutcome =
                        ExtractionDiagnostics.GleaningOutcome.SKIPPED_TOKEN_LIMIT;
            } else {
                glean = invoke(
                        request,
                        1,
                        prompt.systemInstruction(),
                        gleanConversation,
                        metrics);
                validateRoundLimits(request, glean);
                gleaningOutcome = ExtractionDiagnostics.GleaningOutcome.COMPLETED;
            }
        }
        return merge(
                request,
                initial,
                glean,
                new ExtractionDiagnostics(metrics, gleaningOutcome));
    }

    private String boundedSectionContext(ExtractionRequest request) {
        String sectionContext = request.sectionContext();
        if (sectionContext == null) {
            return null;
        }
        EncodedText encoded = tokenizer.encode(sectionContext);
        int limit = request.profile().maxSectionContextTokens();
        if (encoded.size() <= limit) {
            return sectionContext;
        }
        SourceSpan span = encoded.sourceSpan(0, limit);
        return sectionContext.substring(span.startChar(), span.endChar()).strip();
    }

    private ExtractionRoundResponse invoke(
            ExtractionRequest request,
            int round,
            String systemInstruction,
            List<ExtractionConversationMessage> conversation,
            List<ExtractionRoundMetrics> metrics) {
        int estimatedInputTokens = tokenCount(systemInstruction, conversation);
        long started = System.nanoTime();
        ExtractionRoundResponse response = model.extract(new ExtractionRoundRequest(
                request.profile(),
                round,
                systemInstruction,
                conversation));
        metrics.add(new ExtractionRoundMetrics(
                round,
                estimatedInputTokens,
                response.providerInputTokens(),
                response.providerOutputTokens(),
                Duration.ofNanos(System.nanoTime() - started)));
        return response;
    }

    private int tokenCount(
            String systemInstruction,
            List<ExtractionConversationMessage> conversation) {
        int count = tokenizer.count(systemInstruction);
        for (ExtractionConversationMessage message : conversation) {
            count = Math.addExact(count, tokenizer.count(message.content()));
        }
        return count;
    }

    private static void validateRoundLimits(
            ExtractionRequest request,
            ExtractionRoundResponse response) {
        if (response.entities().size() > request.profile().maxEntities()
                || response.relations().size() > request.profile().maxRelations()) {
            throw new IllegalArgumentException(
                    "model extraction round exceeds the configured response limits");
        }
    }

    private static ExtractionResult merge(
            ExtractionRequest request,
            ExtractionRoundResponse initial,
            ExtractionRoundResponse glean,
            ExtractionDiagnostics diagnostics) {
        Map<String, ExtractionCandidateEntity> entities = new LinkedHashMap<>();
        Map<RelationKey, ExtractionCandidateRelation> relations =
                new LinkedHashMap<>();
        mergeRound(entities, relations, initial);
        if (glean != null) {
            mergeRound(entities, relations, glean);
        }

        List<Map.Entry<String, ExtractionCandidateEntity>> orderedEntities =
                entities.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .toList();
        Map<String, String> references = new LinkedHashMap<>();
        List<ExtractedEntity> extractedEntities =
                new ArrayList<>(orderedEntities.size());
        for (int index = 0; index < orderedEntities.size(); index++) {
            Map.Entry<String, ExtractionCandidateEntity> entry =
                    orderedEntities.get(index);
            String reference = "e" + (index + 1);
            references.put(entry.getKey(), reference);
            ExtractionCandidateEntity entity = entry.getValue();
            extractedEntities.add(new ExtractedEntity(
                    reference,
                    entity.name(),
                    entity.type(),
                    entity.description(),
                    entity.confidence()));
        }

        List<ExtractedRelation> extractedRelations = relations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toExtractedRelation(entry.getValue(), references))
                .toList();
        return new ExtractionResult(
                request.profile(),
                extractedEntities,
                extractedRelations,
                diagnostics);
    }

    private static void mergeRound(
            Map<String, ExtractionCandidateEntity> entities,
            Map<RelationKey, ExtractionCandidateRelation> relations,
            ExtractionRoundResponse round) {
        for (ExtractionCandidateEntity candidate : round.entities()) {
            String key = normalizeName(candidate.name());
            entities.merge(
                    key,
                    candidate,
                    LightRagEntityRelationExtractor::preferMoreCompleteEntity);
        }
        for (ExtractionCandidateRelation candidate : round.relations()) {
            if (normalizeName(candidate.sourceName())
                    .equals(normalizeName(candidate.targetName()))) {
                continue;
            }
            relations.merge(
                    RelationKey.from(candidate),
                    candidate,
                    LightRagEntityRelationExtractor::preferMoreCompleteRelation);
        }
    }

    private static ExtractionCandidateEntity preferMoreCompleteEntity(
            ExtractionCandidateEntity previous,
            ExtractionCandidateEntity replacement) {
        if (replacement.description().length() > previous.description().length()) {
            return replacement;
        }
        if (replacement.description().length() == previous.description().length()
                && replacement.confidence() > previous.confidence()) {
            return replacement;
        }
        return previous;
    }

    private static ExtractionCandidateRelation preferMoreCompleteRelation(
            ExtractionCandidateRelation previous,
            ExtractionCandidateRelation replacement) {
        if (replacement.description().length() > previous.description().length()) {
            return replacement;
        }
        if (replacement.description().length() == previous.description().length()
                && replacement.confidence() > previous.confidence()) {
            return replacement;
        }
        return previous;
    }

    private static ExtractedRelation toExtractedRelation(
            ExtractionCandidateRelation relation,
            Map<String, String> references) {
        String sourceReference = references.get(normalizeName(relation.sourceName()));
        String targetReference = references.get(normalizeName(relation.targetName()));
        if (sourceReference == null || targetReference == null) {
            throw new IllegalArgumentException(
                    "final relation endpoint does not resolve to an extracted entity");
        }
        return new ExtractedRelation(
                sourceReference,
                targetReference,
                relation.type(),
                relation.keywords(),
                relation.description(),
                relation.orientation(),
                relation.weight(),
                relation.confidence());
    }

    private static String normalizeName(String value) {
        return Normalizer.normalize(
                        Objects.requireNonNull(value, "value"),
                        Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .strip()
                .toLowerCase(Locale.ROOT);
    }

    private record RelationKey(
            String source,
            String target,
            RelationOrientation orientation)
            implements Comparable<RelationKey> {

        private static final Comparator<RelationKey> ORDER = Comparator
                .comparing(RelationKey::source)
                .thenComparing(RelationKey::target)
                .thenComparing(key -> key.orientation().name());

        static RelationKey from(ExtractionCandidateRelation relation) {
            String source = normalizeName(relation.sourceName());
            String target = normalizeName(relation.targetName());
            if (relation.orientation() == RelationOrientation.UNDIRECTED
                    && source.compareTo(target) > 0) {
                String swapped = source;
                source = target;
                target = swapped;
            }
            return new RelationKey(
                    source,
                    target,
                    relation.orientation());
        }

        @Override
        public int compareTo(RelationKey other) {
            return ORDER.compare(this, other);
        }
    }
}
