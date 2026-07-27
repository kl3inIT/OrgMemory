package com.orgmemory.mcp;

import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
class SkillPublicationController {

    private static final long MAX_PACKAGE_BYTES = 20L * 1024 * 1024;
    private static final Set<String> CLASSIFICATIONS =
            Set.of("PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED");

    private final SkillPublicationApiClient publications;
    private final McpApiAuthorization authorization;

    SkillPublicationController(
            SkillPublicationApiClient publications,
            McpApiAuthorization authorization) {
        this.publications = publications;
        this.authorization = authorization;
    }

    @PostMapping(
            path = "/skill-publications",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    SkillPublicationApiClient.SkillDraftPublication createDraft(
            @RequestPart("file") MultipartFile file,
            @RequestParam String namespace,
            @RequestParam UUID knowledgeSpaceId,
            @RequestParam(defaultValue = "INTERNAL") String classification,
            Authentication authentication) {
        if (file.isEmpty() || file.getSize() > MAX_PACKAGE_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Skill package must be a ZIP no larger than 20 MiB");
        }
        String normalizedClassification = classification.strip().toUpperCase(
                java.util.Locale.ROOT);
        if (!CLASSIFICATIONS.contains(normalizedClassification)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The Skill classification is invalid");
        }
        String downstreamAuthorization;
        try {
            downstreamAuthorization =
                    authorization.requirePublisher(authentication);
        } catch (AssetDeliveryApiClient.AssetDeliveryGatewayException unavailable) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OrgMemory could not authorize Skill publication",
                    unavailable);
        }
        return publications.createDraft(
                downstreamAuthorization,
                file,
                namespace,
                knowledgeSpaceId,
                normalizedClassification);
    }
}
