package com.orgmemory.api.source;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.CreateUploadSourceCommand;
import com.orgmemory.core.knowledge.SourceQueryService;
import com.orgmemory.core.knowledge.SourceUploadService;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sources")
class SourceController {

    private final SourceQueryService sources;
    private final SourceUploadService uploads;
    private final CurrentActorProvider actors;

    SourceController(SourceQueryService sources, SourceUploadService uploads, CurrentActorProvider actors) {
        this.sources = sources;
        this.uploads = uploads;
        this.actors = actors;
    }

    @GetMapping
    @Operation(operationId = "listSources", summary = "List sources uploaded by the current user")
    List<SourceResponse> list(Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        return sources.listOwn(actor).stream().map(SourceResponse::from).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "uploadSource", summary = "Upload a source for asynchronous ingestion")
    @ResponseStatus(HttpStatus.CREATED)
    SourceResponse upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "CONFIDENTIAL") KnowledgeClassification classification,
            Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        try (var content = file.getInputStream()) {
            return SourceResponse.from(uploads.upload(
                    new CreateUploadSourceCommand(
                            actor,
                            file.getOriginalFilename(),
                            file.getContentType(),
                            file.getSize(),
                            classification),
                    content));
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "The uploaded file could not be read", exception);
        }
    }
}
