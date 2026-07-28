package com.orgmemory.mcp;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orgmemory.mcp")
record McpGatewayProperties(
        URI apiBaseUrl,
        Duration connectTimeout,
        Duration requestTimeout,
        URI resourceUri,
        URI authorizationServer,
        URI jwkSetUri,
        String audience,
        String apiAudience) {

    McpGatewayProperties {
        apiBaseUrl = apiBaseUrl == null
                ? URI.create("http://localhost:8080")
                : apiBaseUrl;
        requireHttpUri(apiBaseUrl, "api-base-url");
        connectTimeout = connectTimeout == null
                ? Duration.ofSeconds(5)
                : connectTimeout;
        requirePositive(connectTimeout, "connect-timeout");
        requestTimeout = requestTimeout == null
                ? Duration.ofSeconds(75)
                : requestTimeout;
        requirePositive(requestTimeout, "request-timeout");
        resourceUri = resourceUri == null
                ? URI.create("http://localhost:8090/mcp")
                : resourceUri;
        requireHttpUri(resourceUri, "resource-uri");
        authorizationServer = authorizationServer == null
                ? URI.create("http://localhost:8180/realms/orgmemory")
                : authorizationServer;
        requireHttpUri(authorizationServer, "authorization-server");
        jwkSetUri = jwkSetUri == null
                ? authorizationServer.resolve(
                        authorizationServer.getPath().endsWith("/")
                                ? "protocol/openid-connect/certs"
                                : authorizationServer.getPath()
                                        + "/protocol/openid-connect/certs")
                : jwkSetUri;
        requireHttpUri(jwkSetUri, "jwk-set-uri");
        audience = audience == null || audience.isBlank()
                ? resourceUri.toString()
                : audience.strip();
        apiAudience = apiAudience == null || apiAudience.isBlank()
                ? "orgmemory-web"
                : apiAudience.strip();
    }

    private static void requireHttpUri(URI value, String property) {
        String scheme = value.getScheme();
        if (!value.isAbsolute()
                || value.getHost() == null
                || (!"http".equalsIgnoreCase(scheme)
                        && !"https".equalsIgnoreCase(scheme))
                || value.getQuery() != null
                || value.getFragment() != null) {
            throw new IllegalArgumentException(
                    "orgmemory.mcp." + property
                            + " must be an absolute HTTP(S) URI without query or fragment");
        }
    }

    private static void requirePositive(
            Duration value, String property) {
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(
                    "orgmemory.mcp." + property + " must be positive");
        }
    }
}
