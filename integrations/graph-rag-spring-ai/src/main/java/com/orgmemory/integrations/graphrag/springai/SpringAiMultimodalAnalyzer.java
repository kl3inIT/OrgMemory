package com.orgmemory.integrations.graphrag.springai;

import com.orgmemory.graphrag.multimodal.MultimodalAnalysisContent;
import com.orgmemory.graphrag.multimodal.MultimodalAnalysisOutcome;
import com.orgmemory.graphrag.multimodal.MultimodalAnalysisRequest;
import com.orgmemory.graphrag.multimodal.MultimodalAnalyzer;
import com.orgmemory.graphrag.multimodal.MultimodalAnalyzerRole;
import com.orgmemory.graphrag.multimodal.MultimodalModality;
import com.orgmemory.graphrag.multimodal.MultimodalPayload;
import com.orgmemory.graphrag.multimodal.MultimodalPromptFactory;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;

/** Spring AI model adapter for LightRAG-compatible image, table, and equation analysis. */
public final class SpringAiMultimodalAnalyzer implements MultimodalAnalyzer {

    private final String provider;
    private final MultimodalAnalyzerRole role;
    private final ChatModel chatModel;
    private final MultimodalBinaryResourceLoader resourceLoader;

    public SpringAiMultimodalAnalyzer(
            String provider,
            MultimodalAnalyzerRole role,
            ChatModel chatModel,
            MultimodalBinaryResourceLoader resourceLoader) {
        this.provider = requireText(provider, "provider");
        this.role = Objects.requireNonNull(role, "role");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.resourceLoader =
                Objects.requireNonNull(resourceLoader, "resourceLoader");
    }

    @Override
    public MultimodalAnalyzerRole role() {
        return role;
    }

    @Override
    public MultimodalAnalysisOutcome analyze(MultimodalAnalysisRequest request) {
        Objects.requireNonNull(request, "request");
        if (!provider.equals(request.route().provider())
                || role != request.route().role()) {
            return failure(
                    request,
                    "ROUTE_MISMATCH",
                    "Model route does not match the configured adapter",
                    false);
        }
        if (!validRole(request.item().modality())) {
            return failure(
                    request,
                    "ROLE_MISMATCH",
                    "Analyzer role does not support the requested modality",
                    false);
        }
        try {
            return analyzeWithSchema(request);
        } catch (ArtifactResolutionException exception) {
            return failure(
                    request,
                    "ARTIFACT_UNAVAILABLE",
                    "Binary evidence could not be resolved",
                    exception.transientFailure);
        } catch (RuntimeException exception) {
            return failure(
                    request,
                    "MODEL_INVOCATION_FAILED",
                    "Multimodal model invocation failed",
                    true);
        }
    }

    private MultimodalAnalysisOutcome analyzeWithSchema(
            MultimodalAnalysisRequest request) {
        return switch (request.item().modality()) {
            case IMAGE -> analyze(
                    request,
                    new BeanOutputConverter<>(ImageResponse.class),
                    response -> new MultimodalAnalysisContent.Image(
                            response.name(),
                            response.imageType(),
                            response.description()));
            case TABLE -> analyze(
                    request,
                    new BeanOutputConverter<>(TableResponse.class),
                    response -> new MultimodalAnalysisContent.Table(
                            response.name(), response.description()));
            case EQUATION -> analyze(
                    request,
                    new BeanOutputConverter<>(EquationResponse.class),
                    response -> new MultimodalAnalysisContent.Equation(
                            response.name(),
                            response.equation(),
                            response.description()));
        };
    }

    private <T> MultimodalAnalysisOutcome analyze(
            MultimodalAnalysisRequest request,
            BeanOutputConverter<T> converter,
            Function<T, MultimodalAnalysisContent> mapper) {
        String correction = "";
        for (int attempt = 0; attempt < 2; attempt++) {
            MultimodalPromptFactory.PromptText promptText =
                    MultimodalPromptFactory.render(request, converter.getJsonSchema());
            String responseText = invoke(request, promptText, correction);
            try {
                T response = Objects.requireNonNull(
                        converter.convert(responseText), "structured response");
                MultimodalAnalysisContent content = mapper.apply(response);
                return new MultimodalAnalysisOutcome.Success(
                        request.item().itemId(),
                        request.item().modality(),
                        content,
                        request.evidenceScope(),
                        request.surroundingContext(),
                        request.route(),
                        request.cacheKey());
            } catch (RuntimeException exception) {
                if (attempt == 1) {
                    return failure(
                            request,
                            "INVALID_STRUCTURED_OUTPUT",
                            "Model output failed schema validation after one retry",
                            false);
                }
                correction =
                        "\nThe previous response was invalid. Return only JSON matching the schema.";
            }
        }
        throw new IllegalStateException("unreachable structured-output retry state");
    }

    private String invoke(
            MultimodalAnalysisRequest request,
            MultimodalPromptFactory.PromptText promptText,
            String correction) {
        UserMessage.Builder user = UserMessage.builder()
                .text(promptText.userInstruction() + correction);
        if (request.item().payload() instanceof MultimodalPayload.Image image) {
            Resource resource;
            try {
                resource = Objects.requireNonNull(
                        resourceLoader.load(image.artifact()), "artifact resource");
            } catch (RuntimeException exception) {
                throw new ArtifactResolutionException(exception, true);
            }
            user.media(Media.builder()
                    .id(image.artifact().artifactId())
                    .name(request.item().itemId())
                    .mimeType(MimeType.valueOf(image.artifact().mediaType()))
                    .data(resource)
                    .build());
        }
        Prompt prompt = new Prompt(
                List.of(
                        new SystemMessage(promptText.systemInstruction()),
                        user.build()),
                ChatOptions.builder()
                        .model(request.route().model())
                        .temperature(request.route().temperature())
                        .maxTokens(request.route().maxOutputTokens())
                        .build());
        ChatResponse response = Objects.requireNonNull(
                chatModel.call(prompt), "chat response");
        return requireText(
                Objects.requireNonNull(response.getResult(), "chat generation")
                        .getOutput()
                        .getText(),
                "model response");
    }

    private boolean validRole(MultimodalModality modality) {
        return modality == MultimodalModality.IMAGE
                ? role == MultimodalAnalyzerRole.VISION
                : role == MultimodalAnalyzerRole.TEXT_EXTRACTION;
    }

    private static MultimodalAnalysisOutcome.Failure failure(
            MultimodalAnalysisRequest request,
            String reasonCode,
            String detail,
            boolean transientFailure) {
        return new MultimodalAnalysisOutcome.Failure(
                request.item().itemId(),
                request.item().modality(),
                reasonCode,
                detail,
                transientFailure);
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    record ImageResponse(String name, String imageType, String description) {}

    record TableResponse(String name, String description) {}

    record EquationResponse(String name, String equation, String description) {}

    private static final class ArtifactResolutionException extends RuntimeException {

        private final boolean transientFailure;

        private ArtifactResolutionException(
                RuntimeException cause,
                boolean transientFailure) {
            super(cause);
            this.transientFailure = transientFailure;
        }
    }
}
