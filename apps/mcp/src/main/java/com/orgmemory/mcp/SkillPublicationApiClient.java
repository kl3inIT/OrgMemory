package com.orgmemory.mcp;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@Component
class SkillPublicationApiClient {

    private final RestClient restClient;

    SkillPublicationApiClient(
            @Qualifier("orgMemoryApiRestClientBuilder")
                    RestClient.Builder restClientBuilder,
            McpGatewayProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.apiBaseUrl().toString())
                .build();
    }

    SkillDraftPublication createDraft(
            String authorization,
            MultipartFile file,
            String namespace,
            UUID knowledgeSpaceId,
            String classification) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", file.getResource());
        body.add("namespace", namespace);
        body.add("knowledgeSpaceId", knowledgeSpaceId.toString());
        body.add("classification", classification);
        try {
            SkillDraftPublication response = restClient.post()
                    .uri("/api/assets/skills")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(SkillDraftPublication.class);
            if (response == null) {
                throw unavailable();
            }
            return response;
        } catch (RestClientResponseException refused) {
            int status = refused.getStatusCode().value();
            if (status == 400 || status == 422) {
                throw new ResponseStatusException(
                        refused.getStatusCode(),
                        "The Skill package or publication parameters are invalid");
            }
            if (status == 401 || status == 403) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "The current identity cannot create a Skill in this Knowledge Space");
            }
            if (status == 409) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A Skill already uses this namespace and name");
            }
            if (status == 413) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "The Skill package exceeds the publication limit");
            }
            throw unavailable();
        } catch (RestClientException unavailable) {
            throw unavailable();
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OrgMemory Skill publication is temporarily unavailable");
    }

    record SkillDraftPublication(
            UUID id,
            String type,
            String namespace,
            String slug,
            UUID knowledgeSpaceId,
            Draft draft) {

        record Draft(
                UUID id,
                long lockVersion,
                String title,
                String summary) {
        }
    }
}
