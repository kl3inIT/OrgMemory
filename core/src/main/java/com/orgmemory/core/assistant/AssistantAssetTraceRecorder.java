package com.orgmemory.core.assistant;

import com.orgmemory.core.assetregistry.AssetConsumptionRelease;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class AssistantAssetTraceRecorder {

    private final AssistantAssetTraceRepository traces;
    private final AssistantAssetFeedbackRepository feedback;
    private final ObjectMapper json;

    AssistantAssetTraceRecorder(
            AssistantAssetTraceRepository traces,
            AssistantAssetFeedbackRepository feedback,
            ObjectMapper json) {
        this.traces = traces;
        this.feedback = feedback;
        this.json = json;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID record(
            CurrentActor actor,
            AssistantAssetAction action,
            List<AssistantReleaseRef> releaseRefs,
            List<Map<String, Object>> knowledgeRefs,
            Map<String, Object> modelRoute,
            Map<String, Object> toolCall,
            Map<String, Object> outcome) {
        AssistantAssetTrace trace = traces.saveAndFlush(new AssistantAssetTrace(
                actor.organizationId(),
                actor.userId(),
                action,
                json.writeValueAsString(List.copyOf(releaseRefs)),
                json.writeValueAsString(List.copyOf(knowledgeRefs)),
                json.writeValueAsString(Map.copyOf(modelRoute)),
                json.writeValueAsString(Map.copyOf(toolCall)),
                json.writeValueAsString(Map.of(
                        "actorDerived", true,
                        "objectAuthorization", "live-openfga-and-canonical-acl")),
                json.writeValueAsString(Map.copyOf(outcome)),
                Instant.now()));
        return trace.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID feedback(
            CurrentActor actor,
            AssetConsumptionRelease release,
            AssistantFeedbackType type,
            String comment) {
        return feedback.saveAndFlush(new AssistantAssetFeedback(
                        actor.organizationId(),
                        release.assetId(),
                        release.releaseId(),
                        release.digest(),
                        actor.userId(),
                        type,
                        comment,
                        Instant.now()))
                .getId();
    }
}
