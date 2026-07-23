package com.orgmemory.graphrag.summarization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.port.DescriptionSummaryModel;
import com.orgmemory.graphrag.testkit.CodePointTokenizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IterativeDescriptionSummarizerTests {

    @Test
    void joinsSmallVisibleDescriptionSetsWithoutCallingTheModel() {
        RecordingSummaryModel model = new RecordingSummaryModel();
        IterativeDescriptionSummarizer summarizer =
                new IterativeDescriptionSummarizer(new CodePointTokenizer(), model);

        DescriptionSummaryResult result = summarizer.summarize(
                scoped(List.of("Alpha", "Beta")),
                new DescriptionSummaryOptions(100, 100, 4, "\n", Locale.ENGLISH));

        assertEquals("Alpha\nBeta", result.summary());
        assertFalse(result.modelUsed());
        assertEquals(0, model.requests.size());
    }

    @Test
    void usesIterativeMapReduceWithoutLosingScopeFingerprints() {
        RecordingSummaryModel model = new RecordingSummaryModel();
        IterativeDescriptionSummarizer summarizer =
                new IterativeDescriptionSummarizer(new CodePointTokenizer(), model);

        DescriptionSummaryResult result = summarizer.summarize(
                scoped(List.of(
                        "aaaaaa",
                        "bbbbbb",
                        "cccccc",
                        "dddddd")),
                new DescriptionSummaryOptions(12, 8, 2, "\n", Locale.ENGLISH));

        assertTrue(result.modelUsed());
        assertTrue(result.modelInvocations() >= 2);
        assertFalse(model.requests.isEmpty());
        assertTrue(model.requests.stream().allMatch(request ->
                "auth-fingerprint".equals(request.authorizationFingerprint())
                        && "projection-fingerprint".equals(
                                request.projectionFingerprint())));
    }

    private static ScopedDescriptionSet scoped(List<String> descriptions) {
        return new ScopedDescriptionSet(
                UUID.randomUUID(),
                "Entity",
                "OrgMemory",
                descriptions,
                "auth-fingerprint",
                "projection-fingerprint");
    }

    private static final class RecordingSummaryModel implements DescriptionSummaryModel {

        private final List<DescriptionSummaryRequest> requests = new ArrayList<>();

        @Override
        public String summarize(DescriptionSummaryRequest request) {
            requests.add(request);
            return "summary-" + requests.size();
        }
    }
}
