package com.orgmemory.api.scim;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SCIM discovery")
@SecurityRequirement(name = "scimBearer")
@RestController
@RequestMapping(value = "/scim/v2", produces = ScimErrorWriter.MEDIA_TYPE)
class ScimDiscoveryController {

    private static final String LIST_SCHEMA =
            "urn:ietf:params:scim:api:messages:2.0:ListResponse";

    @GetMapping("/ServiceProviderConfig")
    @Operation(
            operationId = "getScimServiceProviderConfig",
            summary = "Read implemented SCIM capabilities")
    Map<String, Object> serviceProviderConfig() {
        return Map.ofEntries(
                Map.entry("schemas", List.of(
                        "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig")),
                Map.entry("documentationUri", "https://docs.om.kl3in.tech/scim"),
                Map.entry("patch", supported(false)),
                Map.entry("bulk", Map.of(
                        "supported", false,
                        "maxOperations", 0,
                        "maxPayloadSize", 0)),
                Map.entry("filter", Map.of(
                        "supported", false,
                        "maxResults", 0)),
                Map.entry("changePassword", supported(false)),
                Map.entry("sort", supported(false)),
                Map.entry("etag", supported(false)),
                Map.entry("authenticationSchemes", List.of(Map.of(
                        "type", "oauthbearertoken",
                        "name", "Connection bearer credential",
                        "description", "One-time-issued tenant-bound SCIM credential",
                        "specUri", "https://www.rfc-editor.org/rfc/rfc6750",
                        "primary", true))));
    }

    @GetMapping("/ResourceTypes")
    @Operation(
            operationId = "listScimResourceTypes",
            summary = "List implemented SCIM resource types")
    Map<String, Object> resourceTypes() {
        return emptyList();
    }

    @GetMapping("/Schemas")
    @Operation(
            operationId = "listScimSchemas",
            summary = "List implemented SCIM schemas")
    Map<String, Object> schemas() {
        return emptyList();
    }

    private static Map<String, Object> supported(boolean value) {
        return Map.of("supported", value);
    }

    private static Map<String, Object> emptyList() {
        return Map.of(
                "schemas", List.of(LIST_SCHEMA),
                "totalResults", 0,
                "startIndex", 1,
                "itemsPerPage", 0,
                "Resources", List.of());
    }
}
