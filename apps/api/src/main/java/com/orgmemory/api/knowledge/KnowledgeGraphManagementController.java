package com.orgmemory.api.knowledge;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.KnowledgeGraphCurationCommand;
import com.orgmemory.core.knowledge.KnowledgeGraphCurationService;
import com.orgmemory.core.knowledge.KnowledgeGraphExportService;
import com.orgmemory.graphrag.curation.GraphCurationRecord;
import com.orgmemory.graphrag.curation.GraphIdentityKind;
import com.orgmemory.graphrag.export.GraphExportFormat;
import com.orgmemory.graphrag.model.EvidenceReference;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-spaces/{knowledgeSpaceId}/graph")
class KnowledgeGraphManagementController {

    private final KnowledgeGraphCurationService curations;
    private final KnowledgeGraphExportService exports;
    private final CurrentActorProvider actors;

    KnowledgeGraphManagementController(
            KnowledgeGraphCurationService curations,
            KnowledgeGraphExportService exports,
            CurrentActorProvider actors) {
        this.curations = curations;
        this.exports = exports;
        this.actors = actors;
    }

    @PostMapping("/curations/entities")
    @Operation(
            operationId = "curateGraphEntity",
            summary = "Create or edit a governed graph entity")
    GraphCurationRecord curateEntity(
            @PathVariable UUID knowledgeSpaceId,
            @RequestBody CurateEntityRequest request,
            Authentication authentication) {
        return curations.apply(
                actors.current(authentication),
                new KnowledgeGraphCurationCommand.CurateEntity(
                        knowledgeSpaceId,
                        request.idempotencyKey(),
                        request.reason(),
                        request.authorizationGeneration(),
                        request.entityId(),
                        request.name(),
                        request.type(),
                        request.description(),
                        request.evidence().toReference()));
    }

    @PostMapping("/curations/relations")
    @Operation(
            operationId = "curateGraphRelation",
            summary = "Create or edit a governed graph relation")
    GraphCurationRecord curateRelation(
            @PathVariable UUID knowledgeSpaceId,
            @RequestBody CurateRelationRequest request,
            Authentication authentication) {
        return curations.apply(
                actors.current(authentication),
                new KnowledgeGraphCurationCommand.CurateRelation(
                        knowledgeSpaceId,
                        request.idempotencyKey(),
                        request.reason(),
                        request.authorizationGeneration(),
                        request.relationId(),
                        request.sourceEntityId(),
                        request.targetEntityId(),
                        request.type(),
                        request.keywords(),
                        request.description(),
                        request.weight(),
                        request.evidence().toReference()));
    }

    @PostMapping("/curations/aliases")
    @Operation(
            operationId = "mergeGraphIdentity",
            summary = "Merge graph identities through a reversible alias")
    GraphCurationRecord alias(
            @PathVariable UUID knowledgeSpaceId,
            @RequestBody AliasIdentityRequest request,
            Authentication authentication) {
        return curations.apply(
                actors.current(authentication),
                new KnowledgeGraphCurationCommand.AliasIdentity(
                        knowledgeSpaceId,
                        request.idempotencyKey(),
                        request.reason(),
                        request.authorizationGeneration(),
                        request.kind(),
                        request.sourceIdentityId(),
                        request.targetIdentityId()));
    }

    @PostMapping("/curations/suppressions")
    @Operation(
            operationId = "suppressGraphIdentity",
            summary = "Delete an effective graph identity without deleting evidence")
    GraphCurationRecord suppress(
            @PathVariable UUID knowledgeSpaceId,
            @RequestBody SuppressIdentityRequest request,
            Authentication authentication) {
        return curations.apply(
                actors.current(authentication),
                new KnowledgeGraphCurationCommand.SuppressIdentity(
                        knowledgeSpaceId,
                        request.idempotencyKey(),
                        request.reason(),
                        request.authorizationGeneration(),
                        request.kind(),
                        request.identityId()));
    }

    @DeleteMapping("/curations/{curationId}")
    @Operation(
            operationId = "deactivateGraphCuration",
            summary = "Reverse a graph curation record")
    void deactivate(
            @PathVariable UUID knowledgeSpaceId,
            @PathVariable UUID curationId,
            @RequestParam long authorizationGeneration,
            @RequestParam String reason,
            Authentication authentication) {
        curations.deactivate(
                actors.current(authentication),
                knowledgeSpaceId,
                curationId,
                authorizationGeneration,
                reason);
    }

    @GetMapping("/export")
    @Operation(
            operationId = "exportKnowledgeGraph",
            summary = "Export only graph evidence visible to the current user")
    ResponseEntity<String> export(
            @PathVariable UUID knowledgeSpaceId,
            @RequestParam(defaultValue = "JSON") GraphExportFormat format,
            @RequestHeader(value = "X-Request-Id", required = false)
                    String requestId,
            Authentication authentication) {
        var artifact = exports.export(
                actors.current(authentication),
                knowledgeSpaceId,
                format,
                requestId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.mediaType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"knowledge-graph."
                                + artifact.extension()
                                + "\"")
                .body(artifact.content());
    }

    record CurateEntityRequest(
            String idempotencyKey,
            String reason,
            long authorizationGeneration,
            UUID entityId,
            String name,
            String type,
            String description,
            EvidenceRequest evidence) {
    }

    record CurateRelationRequest(
            String idempotencyKey,
            String reason,
            long authorizationGeneration,
            UUID relationId,
            UUID sourceEntityId,
            UUID targetEntityId,
            String type,
            List<String> keywords,
            String description,
            double weight,
            EvidenceRequest evidence) {
    }

    record AliasIdentityRequest(
            String idempotencyKey,
            String reason,
            long authorizationGeneration,
            GraphIdentityKind kind,
            UUID sourceIdentityId,
            UUID targetIdentityId) {
    }

    record SuppressIdentityRequest(
            String idempotencyKey,
            String reason,
            long authorizationGeneration,
            GraphIdentityKind kind,
            UUID identityId) {
    }

    record EvidenceRequest(
            UUID organizationId,
            UUID knowledgeAssetId,
            UUID sourceRevisionId,
            UUID chunkId,
            UUID aclSnapshotId,
            long aclGeneration) {

        EvidenceReference toReference() {
            return new EvidenceReference(
                    organizationId,
                    knowledgeAssetId,
                    sourceRevisionId,
                    chunkId,
                    aclSnapshotId,
                    aclGeneration);
        }
    }
}
