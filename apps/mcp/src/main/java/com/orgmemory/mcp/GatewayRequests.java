package com.orgmemory.mcp;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

final class GatewayRequests {

    static final FailureMessages ASSET = new FailureMessages(
            "OrgMemory returned an empty Asset response",
            "The Asset request is invalid",
            "The requested Asset is not available to the current identity",
            "OrgMemory Asset delivery is temporarily unavailable");
    static final FailureMessages KNOWLEDGE_SEARCH = new FailureMessages(
            "OrgMemory returned an empty search response",
            "The knowledge search request is invalid",
            "The current identity cannot access that knowledge",
            "OrgMemory knowledge search is temporarily unavailable");

    private GatewayRequests() {
    }

    static <T> T request(ApiCall<T> call, FailureMessages messages) {
        try {
            T response = call.execute();
            if (response == null) {
                throw new McpGatewayException(messages.empty());
            }
            return response;
        } catch (RestClientResponseException refused) {
            int status = refused.getStatusCode().value();
            if (status == 400) {
                throw new McpGatewayException(messages.invalid());
            }
            if (status == 401 || status == 403 || status == 404) {
                throw new McpGatewayException(messages.unavailableToIdentity());
            }
            throw new McpGatewayException(messages.temporarilyUnavailable());
        } catch (RestClientException unavailable) {
            throw new McpGatewayException(
                    messages.temporarilyUnavailable(), unavailable);
        }
    }

    record FailureMessages(
            String empty,
            String invalid,
            String unavailableToIdentity,
            String temporarilyUnavailable) {
    }

    @FunctionalInterface
    interface ApiCall<T> {

        T execute();
    }
}
