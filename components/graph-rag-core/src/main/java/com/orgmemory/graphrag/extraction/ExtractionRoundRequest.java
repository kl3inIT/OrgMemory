package com.orgmemory.graphrag.extraction;

import static com.orgmemory.graphrag.validation.TextValidation.requireText;

import com.orgmemory.graphrag.model.ExtractionProfile;
import java.util.List;
import java.util.Objects;

public record ExtractionRoundRequest(
        ExtractionProfile profile,
        int round,
        String systemInstruction,
        List<ExtractionConversationMessage> conversation) {

    public ExtractionRoundRequest {
        Objects.requireNonNull(profile, "profile");
        if (round < 0 || round > profile.maxGleaningRounds()) {
            throw new IllegalArgumentException("round is outside the extraction profile");
        }
        systemInstruction = requireText(systemInstruction, "systemInstruction");
        conversation = List.copyOf(Objects.requireNonNull(conversation, "conversation"));
        if (conversation.isEmpty()
                || conversation.getLast().role()
                        != ExtractionConversationMessage.Role.USER) {
            throw new IllegalArgumentException(
                    "conversation must end with the current user instruction");
        }
    }

}
