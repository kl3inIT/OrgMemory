package com.orgmemory.integrations.graphrag.springai;

import com.orgmemory.graphrag.model.ExtractedEntity;
import com.orgmemory.graphrag.model.ExtractedRelation;
import com.orgmemory.graphrag.model.ExtractionRequest;
import com.orgmemory.graphrag.model.ExtractionResult;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.port.EntityRelationExtractor;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

public final class SpringAiEntityRelationExtractor implements EntityRelationExtractor {

    public static final String PROMPT_VERSION = GraphExtractionPrompt.VERSION;

    private final String provider;
    private final ChatClient chatClient;

    public SpringAiEntityRelationExtractor(String provider, ChatModel chatModel) {
        this.provider = requireText(provider, "provider");
        this.chatClient = ChatClient.builder(Objects.requireNonNull(chatModel, "chatModel"))
                .build();
    }

    @Override
    public ExtractionResult extract(ExtractionRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            requireMatchingProvider(request);
            GraphExtractionPrompt.RenderedPrompt prompt = GraphExtractionPrompt.render(request);
            StructuredExtractionResponse response = chatClient
                    .prompt()
                    .options(ChatOptions.builder()
                            .model(request.profile().model())
                            .temperature(0.0))
                    .system(prompt.systemInstruction())
                    .user(prompt.userInstruction())
                    .call()
                    .entity(StructuredExtractionResponse.class);
            return map(request, response);
        } catch (GraphExtractionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GraphExtractionException(
                    "Structured entity-relation extraction failed",
                    exception);
        }
    }

    private void requireMatchingProvider(ExtractionRequest request) {
        if (!provider.equals(request.profile().provider())) {
            throw new GraphExtractionException(
                    "Extraction profile provider does not match the configured Spring AI adapter");
        }
    }

    private static ExtractionResult map(
            ExtractionRequest request,
            StructuredExtractionResponse response) {
        if (response == null || response.entities() == null || response.relations() == null) {
            throw new GraphExtractionException(
                    "Structured extraction response must contain entity and relation arrays");
        }
        List<ExtractedEntity> entities = response.entities().stream()
                .map(SpringAiEntityRelationExtractor::mapEntity)
                .toList();
        List<ExtractedRelation> relations = response.relations().stream()
                .map(SpringAiEntityRelationExtractor::mapRelation)
                .toList();
        return new ExtractionResult(request.profile(), entities, relations);
    }

    private static ExtractedEntity mapEntity(StructuredExtractionResponse.EntityItem entity) {
        Objects.requireNonNull(entity, "entity");
        return new ExtractedEntity(
                entity.reference(),
                entity.name(),
                entity.type(),
                entity.description(),
                requireConfidence(entity.confidence()));
    }

    private static ExtractedRelation mapRelation(StructuredExtractionResponse.RelationItem relation) {
        Objects.requireNonNull(relation, "relation");
        List<String> keywords = Objects.requireNonNull(relation.keywords(), "keywords").stream()
                .map(keyword -> requireText(keyword, "keyword"))
                .distinct()
                .toList();
        return new ExtractedRelation(
                relation.sourceReference(),
                relation.targetReference(),
                relation.type(),
                keywords,
                relation.description(),
                parseOrientation(relation.orientation()),
                requireConfidence(relation.confidence()));
    }

    private static RelationOrientation parseOrientation(String value) {
        String normalized = requireText(value, "orientation").toUpperCase(Locale.ROOT);
        try {
            return RelationOrientation.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "orientation must be DIRECTED or UNDIRECTED",
                    exception);
        }
    }

    private static double requireConfidence(Double value) {
        return Objects.requireNonNull(value, "confidence");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
