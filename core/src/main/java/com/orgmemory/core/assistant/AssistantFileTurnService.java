package com.orgmemory.core.assistant;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class AssistantFileTurnService {

    private final AssistantFileService files;
    private final AssistantTurnFileRepository bindings;

    AssistantFileTurnService(AssistantFileService files, AssistantTurnFileRepository bindings) {
        this.files = files;
        this.bindings = bindings;
    }

    AssistantPrivateFileSelection claim(
            CurrentActor actor,
            UUID conversationId,
            UUID turnId,
            UUID userMessageId,
            List<UUID> requestedIds) {
        List<UUID> ids = List.copyOf(requestedIds == null ? List.of() : requestedIds);
        if (ids.isEmpty() || ids.size() > 3 || new LinkedHashSet<>(ids).size() != ids.size()) {
            throw new BusinessValidationException(
                    "assistant.file-selection-invalid",
                    "Select between one and three distinct Assistant files");
        }
        var selected = new ArrayList<AssistantPrivateFileSelection.Item>(ids.size());
        var stored = new ArrayList<AssistantTurnFile>(ids.size());
        for (int index = 0; index < ids.size(); index++) {
            AssistantFile file = files.requireUsableOwner(actor, ids.get(index), true);
            selected.add(new AssistantPrivateFileSelection.Item(
                    file.getId(), file.processingGeneration()));
            stored.add(new AssistantTurnFile(
                    UUID.randomUUID(),
                    turnId,
                    userMessageId,
                    file.getId(),
                    file.processingGeneration(),
                    actor.organizationId(),
                    conversationId,
                    actor.userId(),
                    index + 1));
        }
        bindings.saveAll(stored);
        return new AssistantPrivateFileSelection(selected);
    }
}
