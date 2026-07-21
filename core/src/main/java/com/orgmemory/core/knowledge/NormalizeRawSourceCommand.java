package com.orgmemory.core.knowledge;

import java.util.UUID;

public record NormalizeRawSourceCommand(
        UUID organizationId,
        UUID rawSourceObjectId,
        String normalizerVersion,
        String title,
        String normalizedContent,
        String language) {
}
