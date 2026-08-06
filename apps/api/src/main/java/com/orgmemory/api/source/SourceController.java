package com.orgmemory.api.source;

import com.orgmemory.core.knowledge.sourceledger.CreateUploadSourceCommand;
import com.orgmemory.core.knowledge.sourceledger.SourceListCommand;
import com.orgmemory.core.knowledge.sourceledger.SourceListStatus;
import com.orgmemory.core.knowledge.sourceledger.SourceQueryService;
import com.orgmemory.core.knowledge.sourceledger.SourceUploadService;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetLifecycleService;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetRef;

import com.orgmemory.api.ApiRequestException;
import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/sources")
class SourceController {

    private final SourceQueryService sources;
    private final SourceUploadService uploads;
    private final CurrentActorProvider actors;
    private final KnowledgeAssetLifecycleService lifecycle;

    SourceController(
            SourceQueryService sources,
            SourceUploadService uploads,
            CurrentActorProvider actors,
            KnowledgeAssetLifecycleService lifecycle) {
        this.sources = sources;
        this.uploads = uploads;
        this.actors = actors;
        this.lifecycle = lifecycle;
    }

    @GetMapping
    @Operation(operationId = "listSources", summary = "List sources visible to the current user")
    SourcePageResponse list(
            @RequestParam(required = false) UUID knowledgeSpaceId,
            @RequestParam(required = false) KnowledgeClassification classification,
            @RequestParam(required = false) SourceListStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") int pageSize,
            Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        return SourcePageResponse.from(sources.listVisible(
                actor,
                new SourceListCommand(
                        knowledgeSpaceId,
                        classification,
                        status,
                        q,
                        cursor,
                        pageSize)));
    }

    @DeleteMapping("/{sourceId}")
    @Operation(operationId = "deleteSource", summary = "Retire a ready manual-upload document")
    KnowledgeAssetRef delete(
            @PathVariable UUID sourceId,
            Authentication authentication) {
        return lifecycle.deleteSource(actors.current(authentication), sourceId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "uploadSource", summary = "Upload a source for asynchronous ingestion")
    @ResponseStatus(HttpStatus.CREATED)
    SourceResponse upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "CONFIDENTIAL") KnowledgeClassification classification,
            @RequestParam UUID knowledgeSpaceId,
            Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        try (var content = file.getInputStream()) {
            return SourceResponse.from(uploads.upload(
                    new CreateUploadSourceCommand(
                            actor,
                            file.getOriginalFilename(),
                            file.getSize(),
                            classification,
                            knowledgeSpaceId),
                    content));
        } catch (IOException exception) {
            throw new ApiRequestException(
                    "The uploaded file could not be read",
                    exception);
        }
    }
}
