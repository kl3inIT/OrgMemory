package com.orgmemory.api.knowledge;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.GraphIndexJobView;
import com.orgmemory.core.knowledge.GraphIndexLifecycleService;
import com.orgmemory.core.knowledge.KnowledgeAssetLifecycleService;
import com.orgmemory.core.knowledge.KnowledgeAssetRef;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-assets")
class KnowledgeAssetLifecycleController {

    private final KnowledgeAssetLifecycleService assets;
    private final GraphIndexLifecycleService graphJobs;
    private final CurrentActorProvider actors;

    KnowledgeAssetLifecycleController(
            KnowledgeAssetLifecycleService assets,
            GraphIndexLifecycleService graphJobs,
            CurrentActorProvider actors) {
        this.assets = assets;
        this.graphJobs = graphJobs;
        this.actors = actors;
    }

    @DeleteMapping("/{knowledgeAssetId}")
    @Operation(
            operationId = "deleteKnowledgeAsset",
            summary = "Retire a Knowledge Asset and remove its derived graph")
    KnowledgeAssetRef delete(
            @PathVariable UUID knowledgeAssetId,
            Authentication authentication) {
        return assets.delete(
                actors.current(authentication), knowledgeAssetId);
    }

    @GetMapping("/graph-jobs/{jobId}")
    @Operation(
            operationId = "getGraphIndexJob",
            summary = "Read graph indexing lifecycle status")
    GraphIndexJobView graphJob(
            @PathVariable UUID jobId, Authentication authentication) {
        return graphJobs.status(actors.current(authentication), jobId);
    }

    @PostMapping("/graph-jobs/{jobId}/cancel")
    @Operation(
            operationId = "cancelGraphIndexJob",
            summary = "Cancel queued or in-flight graph indexing")
    GraphIndexJobView cancelGraphJob(
            @PathVariable UUID jobId, Authentication authentication) {
        return graphJobs.cancel(actors.current(authentication), jobId);
    }

    @PostMapping("/graph-jobs/{jobId}/resume")
    @Operation(
            operationId = "resumeGraphIndexJob",
            summary = "Resume unfinished graph indexing")
    @ResponseStatus(HttpStatus.ACCEPTED)
    GraphIndexJobView resumeGraphJob(
            @PathVariable UUID jobId, Authentication authentication) {
        return graphJobs.resume(actors.current(authentication), jobId);
    }
}
