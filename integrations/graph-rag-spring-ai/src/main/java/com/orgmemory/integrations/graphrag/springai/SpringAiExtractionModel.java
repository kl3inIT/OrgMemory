package com.orgmemory.integrations.graphrag.springai;

import com.orgmemory.graphrag.extraction.ExtractionCandidateEntity;
import com.orgmemory.graphrag.extraction.ExtractionCandidateRelation;
import com.orgmemory.graphrag.extraction.ExtractionConversationMessage;
import com.orgmemory.graphrag.extraction.ExtractionRoundRequest;
import com.orgmemory.graphrag.extraction.ExtractionRoundResponse;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.port.ExtractionModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;

/** Spring AI effect adapter for one core-owned extraction round. */
final class SpringAiExtractionModel implements ExtractionModel {

    private final String provider;
    private final ChatModel chatModel;
    private final BeanOutputConverter<StructuredExtractionResponse> converter =
            new BeanOutputConverter<>(StructuredExtractionResponse.class);

    SpringAiExtractionModel(String provider, ChatModel chatModel) {
        this.provider = requireText(provider, "provider");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
    }

    @Override
    public ExtractionRoundResponse extract(ExtractionRoundRequest request) {
        Objects.requireNonNull(request, "request");
        if (!provider.equals(request.profile().provider())) {
            throw new GraphExtractionException(
                    "Extraction profile provider does not match the configured Spring AI adapter");
        }
        List<Message> messages = new ArrayList<>(request.conversation().size() + 1);
        messages.add(new SystemMessage(request.systemInstruction()));
        request.conversation().stream()
                .map(SpringAiExtractionModel::toMessage)
                .forEach(messages::add);
        ChatResponse chatResponse = chatModel.call(new Prompt(
                messages,
                ChatOptions.builder()
                        .model(request.profile().model())
                        .temperature(0.0)
                        .build()));
        if (chatResponse == null
                || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null) {
            throw new GraphExtractionException("Extraction model returned no assistant response");
        }
        String assistantMessage = chatResponse.getResult().getOutput().getText();
        StructuredExtractionResponse structured = converter.convert(assistantMessage);
        if (structured == null
                || structured.entities() == null
                || structured.relationships() == null) {
            throw new GraphExtractionException(
                    "Structured extraction response must contain entity and relationship arrays");
        }
        Usage usage = chatResponse.getMetadata() == null
                ? null
                : chatResponse.getMetadata().getUsage();
        return new ExtractionRoundResponse(
                assistantMessage,
                structured.entities().stream()
                        .map(SpringAiExtractionModel::mapEntity)
                        .toList(),
                structured.relationships().stream()
                        .map(SpringAiExtractionModel::mapRelation)
                        .toList(),
                tokenCount(usage == null ? null : usage.getPromptTokens()),
                tokenCount(usage == null ? null : usage.getCompletionTokens()));
    }

    private static Message toMessage(ExtractionConversationMessage message) {
        return switch (message.role()) {
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }

    private static ExtractionCandidateEntity mapEntity(
            StructuredExtractionResponse.EntityItem entity) {
        Objects.requireNonNull(entity, "entity");
        return new ExtractionCandidateEntity(
                entity.name(),
                entity.type(),
                entity.description(),
                requireConfidence(entity.confidence()));
    }

    private static ExtractionCandidateRelation mapRelation(
            StructuredExtractionResponse.RelationItem relation) {
        Objects.requireNonNull(relation, "relation");
        List<String> keywords = Objects.requireNonNull(
                        relation.keywords(), "keywords")
                .stream()
                .map(keyword -> requireText(keyword, "keyword"))
                .distinct()
                .toList();
        return new ExtractionCandidateRelation(
                relation.source(),
                relation.target(),
                relation.type(),
                keywords,
                relation.description(),
                parseOrientation(relation.orientation()),
                1.0,
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

    private static int tokenCount(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static double requireConfidence(Double value) {
        return Objects.requireNonNull(value, "confidence");
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
