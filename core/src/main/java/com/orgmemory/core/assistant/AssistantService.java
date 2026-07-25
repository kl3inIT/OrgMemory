package com.orgmemory.core.assistant;

import com.orgmemory.core.ai.AiGatewayUnavailableException;
import com.orgmemory.core.ai.ChatGenerationRequest;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.ai.ChatModelPort;
import com.orgmemory.core.knowledge.PermissionAwareKnowledgeSearch;
import com.orgmemory.core.knowledge.RetrievedKnowledgeEvidence;
import com.orgmemory.core.organization.CurrentActor;
import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Flux;

public class AssistantService {

    static final String NO_ACCESSIBLE_EVIDENCE =
            "I could not find enough accessible company knowledge to answer that question.";

    private final PermissionAwareKnowledgeSearch retrieval;
    private final ChatModelPort chat;

    public AssistantService(
            PermissionAwareKnowledgeSearch retrieval,
            ChatModelPort chat) {
        this.retrieval = retrieval;
        this.chat = chat;
    }

    public AssistantTurn startTurn(
            CurrentActor actor,
            String question,
            Integer requestedLimit,
            String requestId,
            String conversationId) {
        var search = retrieval.search(actor, question, requestedLimit, requestId);
        if (search.evidence().isEmpty()) {
            return new AssistantTurn(search.requestId(), List.of(), Flux.just(NO_ACCESSIBLE_EVIDENCE));
        }

        PreparedTurn prepared = search.grounding()
                .map(grounding -> new PreparedTurn(
                        AssistantPromptFactory.addUserContext(
                                grounding.generationRequest(),
                                actor),
                        numbered(grounding.citations())))
                .orElseGet(() -> {
                    AssistantPromptFactory.PreparedPrompt prompt =
                            AssistantPromptFactory.create(
                                    question,
                                    search.evidence(),
                                    actor);
                    return new PreparedTurn(
                            prompt.request(),
                            prompt.citations());
                });
        Flux<String> content;
        try {
            content = chat.stream(
                            AiWorkload.ASSISTANT_CHAT,
                            prepared.request(),
                            conversationId)
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
        return new AssistantTurn(
                search.requestId(),
                prepared.citations(),
                content);
    }

    private static List<AssistantCitation> numbered(
            List<RetrievedKnowledgeEvidence> evidence) {
        ArrayList<AssistantCitation> citations =
                new ArrayList<>(evidence.size());
        for (int index = 0; index < evidence.size(); index++) {
            citations.add(new AssistantCitation(index + 1, evidence.get(index)));
        }
        return List.copyOf(citations);
    }

    private record PreparedTurn(
            ChatGenerationRequest request,
            List<AssistantCitation> citations) {

        private PreparedTurn {
            citations = List.copyOf(citations);
        }
    }
}
