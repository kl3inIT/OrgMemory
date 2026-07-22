package com.orgmemory.core.assistant;

import com.orgmemory.core.ai.AiGatewayUnavailableException;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.ai.ChatModelPort;
import com.orgmemory.core.knowledge.SecureKnowledgeRetrievalService;
import com.orgmemory.core.organization.CurrentActor;
import reactor.core.publisher.Flux;

public class AssistantService {

    static final String NO_ACCESSIBLE_EVIDENCE =
            "I could not find enough accessible company knowledge to answer that question.";

    private final SecureKnowledgeRetrievalService retrieval;
    private final ChatModelPort chat;

    public AssistantService(SecureKnowledgeRetrievalService retrieval, ChatModelPort chat) {
        this.retrieval = retrieval;
        this.chat = chat;
    }

    public AssistantTurn startTurn(
            CurrentActor actor,
            String question,
            Integer requestedLimit,
            String requestId) {
        var search = retrieval.search(actor, question, requestedLimit, requestId);
        if (search.evidence().isEmpty()) {
            return new AssistantTurn(search.requestId(), search.evidence(), Flux.just(NO_ACCESSIBLE_EVIDENCE));
        }

        Flux<String> content;
        try {
            content = chat.stream(
                            AiWorkload.ASSISTANT_CHAT,
                            AssistantPromptFactory.create(question, search.evidence()))
                    .filter(token -> token != null && !token.isEmpty())
                    .switchIfEmpty(Flux.error(new AssistantUnavailableException(
                            "The assistant returned no answer")))
                    .onErrorMap(
                            error -> !(error instanceof AssistantUnavailableException),
                            error -> new AssistantUnavailableException("The assistant is unavailable", error));
        } catch (AiGatewayUnavailableException exception) {
            throw new AssistantUnavailableException("The assistant is unavailable", exception);
        } catch (RuntimeException exception) {
            throw new AssistantUnavailableException("The assistant is unavailable", exception);
        }
        return new AssistantTurn(search.requestId(), search.evidence(), content);
    }
}
