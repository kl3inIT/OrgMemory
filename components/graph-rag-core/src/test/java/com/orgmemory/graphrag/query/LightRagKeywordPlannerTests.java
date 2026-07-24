package com.orgmemory.graphrag.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class LightRagKeywordPlannerTests {

    @Test
    void trustedKeywordsBypassTheModelAndRemainExplicitlyAttributed() {
        RecordingModel model = new RecordingModel(
                new KeywordPlan(List.of("ignored"), List.of("ignored"), KeywordPlan.Source.MODEL));
        LightRagKeywordPlanner planner = new LightRagKeywordPlanner(model, "Vietnamese");

        KeywordPlan result = planner.plan(
                "Chính sách thử việc là gì?",
                new KeywordPlan(
                        List.of("employee policy"),
                        List.of("probation"),
                        KeywordPlan.Source.MODEL));

        assertEquals(0, model.calls);
        assertEquals(KeywordPlan.Source.TRUSTED_CALLER, result.source());
        assertEquals(List.of("employee policy"), result.highLevel());
        assertEquals(List.of("probation"), result.lowLevel());
    }

    @Test
    void emptyModelResultFallsBackToTheOriginalShortQuery() {
        RecordingModel model = new RecordingModel(KeywordPlan.empty(KeywordPlan.Source.MODEL));

        KeywordPlan result =
                new LightRagKeywordPlanner(model, "Vietnamese").plan("probation policy", null);

        assertEquals(1, model.calls);
        assertEquals(KeywordPlan.Source.SHORT_QUERY_FALLBACK, result.source());
        assertEquals(List.of("probation policy"), result.lowLevel());
        assertTrue(result.highLevel().isEmpty());
    }

    @Test
    void emptyModelResultDoesNotInventKeywordsForLongQueries() {
        RecordingModel model = new RecordingModel(KeywordPlan.empty(KeywordPlan.Source.MODEL));
        String query = "x".repeat(50);

        KeywordPlan result =
                new LightRagKeywordPlanner(model, "Vietnamese").plan(query, null);

        assertEquals(1, model.calls);
        assertTrue(result.empty());
        assertEquals(KeywordPlan.Source.MODEL, result.source());
    }

    private static final class RecordingModel implements KeywordPlanningModel {

        private final KeywordPlan result;
        private int calls;

        private RecordingModel(KeywordPlan result) {
            this.result = result;
        }

        @Override
        public ProcessingComponentRef component() {
            return new ProcessingComponentRef("keyword-test", "1");
        }

        @Override
        public KeywordPlan complete(String prompt) {
            calls++;
            return result;
        }
    }
}
