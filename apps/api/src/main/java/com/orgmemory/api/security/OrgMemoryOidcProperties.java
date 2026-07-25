package com.orgmemory.api.security;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.util.Assert;

@ConfigurationProperties("orgmemory.security.oidc")
record OrgMemoryOidcProperties(
        URI issuerUri,
        String clientId,
        String clientSecret,
        URI webBaseUrl,
        String audience) {

    @ConstructorBinding
    OrgMemoryOidcProperties {
        Assert.notNull(issuerUri, "orgmemory.security.oidc.issuer-uri is required");
        Assert.hasText(clientId, "orgmemory.security.oidc.client-id is required");
        Assert.hasText(clientSecret, "orgmemory.security.oidc.client-secret is required");
        Assert.notNull(webBaseUrl, "orgmemory.security.oidc.web-base-url is required");
        audience = audience == null || audience.isBlank()
                ? clientId
                : audience.strip();
    }

    OrgMemoryOidcProperties(
            URI issuerUri,
            String clientId,
            String clientSecret,
            URI webBaseUrl) {
        this(issuerUri, clientId, clientSecret, webBaseUrl, clientId);
    }

    String endpoint(String path) {
        String issuer = issuerUri.toString();
        return (issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer) + path;
    }

    String webLocation(String path) {
        return webBaseUrl.resolve(path).toString();
    }
}
