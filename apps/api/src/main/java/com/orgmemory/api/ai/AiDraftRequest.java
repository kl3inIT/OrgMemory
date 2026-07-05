package com.orgmemory.api.ai;

import jakarta.validation.constraints.NotBlank;

record AiDraftRequest(
        @NotBlank String rawText,
        String aiTool,
        String businessProcess) {
}
