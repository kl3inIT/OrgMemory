package com.orgmemory.core.assistant;

public record AssistantPrivateFileTurnClaim(
        AssistantTurnRef turn,
        AssistantPrivateFileSelection selection) {}
