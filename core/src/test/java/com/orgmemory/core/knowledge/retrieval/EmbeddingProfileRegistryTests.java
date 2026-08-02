package com.orgmemory.core.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class EmbeddingProfileRegistryTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();

    private final EmbeddingProfileRepository profiles = mock(EmbeddingProfileRepository.class);
    private final EmbeddingProfileRegistry registry =
            new JdbcEmbeddingProfileRegistry(profiles, mock(JdbcClient.class));

    @Test
    void findsAnImmutableProfileByTenantAndId() {
        EmbeddingProfile profile = mock(EmbeddingProfile.class);
        EmbeddingProfileRef expected = new EmbeddingProfileRef(
                PROFILE_ID,
                ORGANIZATION_ID,
                "openai/text-embedding-3-large/1536/cosine",
                "openai",
                "text-embedding-3-large",
                1536,
                EmbeddingDistanceMetric.COSINE);
        when(profiles.findByIdAndOrganizationId(PROFILE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(profile));
        when(profile.toRef()).thenReturn(expected);

        assertEquals(expected, registry.findById(ORGANIZATION_ID, PROFILE_ID).orElseThrow());
    }

    @Test
    void doesNotExposeAnotherTenantOrMissingProfile() {
        assertTrue(registry.findById(ORGANIZATION_ID, PROFILE_ID).isEmpty());
    }
}
