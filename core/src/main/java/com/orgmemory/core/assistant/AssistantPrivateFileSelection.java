package com.orgmemory.core.assistant;

import java.util.List;
import java.util.UUID;

public record AssistantPrivateFileSelection(List<Item> files) {
    public AssistantPrivateFileSelection {
        files = List.copyOf(files == null ? List.of() : files);
        if (files.size() > 3) throw new IllegalArgumentException("at most three files may be selected");
    }

    public boolean restricted() { return !files.isEmpty(); }

    public record Item(UUID fileId, long processingGeneration) {}
}
