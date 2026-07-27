package com.orgmemory.api.scim;

import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.util.Assert;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("orgmemory.security.scim")
public record ScimSecurityProperties(
        String verifierKey,
        int currentKeyVersion,
        boolean requireTls,
        DataSize maximumRequestSize,
        int requestsPerMinute,
        Duration tokenTtl,
        Duration rotationOverlap) {

    @ConstructorBinding
    public ScimSecurityProperties {
        Assert.hasText(verifierKey, "orgmemory.security.scim.verifier-key is required");
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(verifierKey);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "orgmemory.security.scim.verifier-key must be unpadded base64url", invalid);
        }
        Assert.isTrue(decoded.length >= 32, "SCIM verifier key must contain at least 256 bits");
        Assert.isTrue(currentKeyVersion > 0, "SCIM verifier key version must be positive");
        maximumRequestSize = maximumRequestSize == null
                ? DataSize.ofKilobytes(256)
                : maximumRequestSize;
        Assert.isTrue(maximumRequestSize.toBytes() > 0, "SCIM request size must be positive");
        requestsPerMinute = requestsPerMinute <= 0 ? 120 : requestsPerMinute;
        tokenTtl = tokenTtl == null ? Duration.ofDays(365) : tokenTtl;
        rotationOverlap = rotationOverlap == null ? Duration.ofMinutes(15) : rotationOverlap;
        Assert.isTrue(!tokenTtl.isNegative() && !tokenTtl.isZero(), "SCIM token TTL must be positive");
        Assert.isTrue(
                !rotationOverlap.isNegative() && rotationOverlap.compareTo(Duration.ofHours(24)) <= 0,
                "SCIM rotation overlap must be between zero and 24 hours");
    }

    public byte[] decodedVerifierKey() {
        return Base64.getUrlDecoder().decode(verifierKey);
    }
}
