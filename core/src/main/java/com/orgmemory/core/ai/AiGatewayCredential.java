package com.orgmemory.core.ai;

import com.orgmemory.core.shared.BaseEntity;
import com.orgmemory.core.shared.secret.EncryptedSecret;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_gateway_credentials")
class AiGatewayCredential extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "gateway_profile_id", nullable = false, updatable = false)
    private UUID gatewayProfileId;

    @Column(name = "cipher_text", nullable = false)
    private String cipherText;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Column(name = "set_by_user_id", nullable = false)
    private UUID setByUserId;

    @Column(name = "set_at", nullable = false)
    private Instant setAt;

    protected AiGatewayCredential() {
    }

    AiGatewayCredential(
            UUID organizationId,
            UUID gatewayProfileId,
            EncryptedSecret secret,
            UUID setByUserId,
            Instant setAt) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.gatewayProfileId = gatewayProfileId;
        replace(secret, setByUserId, setAt);
    }

    void replace(EncryptedSecret secret, UUID setByUserId, Instant setAt) {
        this.cipherText = secret.cipherText();
        this.keyVersion = secret.keyVersion();
        this.setByUserId = setByUserId;
        this.setAt = setAt;
    }

    EncryptedSecret stored() {
        return new EncryptedSecret(cipherText, keyVersion);
    }

    UUID setByUserId() {
        return setByUserId;
    }

    Instant setAt() {
        return setAt;
    }

    @Override
    public String toString() {
        return "AiGatewayCredential[profileId=%s, keyVersion=%d, setAt=%s]"
                .formatted(gatewayProfileId, keyVersion, setAt);
    }
}
