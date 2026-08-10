package com.orgmemory.core.knowledge.sourceledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceIngestionJobProcessingProfileTests {

    @Test
    void keepsTheFirstRequestedSnapshotWhenRuntimeDefaultsChangeBeforeRetry() {
        SourceIngestionJob job = job();
        DocumentProcessingProfileSnapshot policyV1 =
                DocumentProcessingProfileSnapshot.from("policy=structured-block-v1\n");
        DocumentProcessingProfileSnapshot policyV2 =
                DocumentProcessingProfileSnapshot.from("policy=structured-block-v2\n");

        assertEquals(policyV1, job.bindRequestedProcessingProfile(policyV1));
        assertEquals(policyV1, job.bindRequestedProcessingProfile(policyV2));
    }

    @Test
    void refusesASecondResolvedProfileForTheSameRevision() {
        SourceIngestionJob job = job();
        DocumentProcessingProfileSnapshot first =
                DocumentProcessingProfileSnapshot.from("actual=paragraph-semantic\n");
        DocumentProcessingProfileSnapshot changed =
                DocumentProcessingProfileSnapshot.from("actual=recursive-character\n");

        job.bindResolvedProcessingProfile(first);

        assertThrows(
                ProcessingProfileMismatchException.class,
                () -> job.bindResolvedProcessingProfile(changed));
        assertEquals(first, job.resolvedProcessingProfile().orElseThrow());
    }

    private static SourceIngestionJob job() {
        return new SourceIngestionJob(
                UUID.randomUUID(), UUID.randomUUID(), 5, Instant.parse("2026-08-10T00:00:00Z"));
    }
}
