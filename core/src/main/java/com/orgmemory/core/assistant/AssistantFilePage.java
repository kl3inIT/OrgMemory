package com.orgmemory.core.assistant;

import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AssistantFilePage(
        @NotNull List<AssistantFileView> items,
        @PositiveOrZero int page,
        boolean hasMore) {
    public AssistantFilePage {
        items = List.copyOf(items == null ? List.of() : items);
    }
}
