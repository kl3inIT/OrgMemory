package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.BaseEntity;
import com.orgmemory.core.shared.secret.EncryptedSecret;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One connection's credential, encrypted. The entity holds ciphertext and never the token: the
 * only way to a usable value is through the cipher, which keeps the decision to decrypt in one
 * place instead of wherever an entity happens to be loaded.
 */
@Entity
@Table(name = "source_connection_credentials")
class SourceConnectionCredential extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "source_connection_id", nullable = false, updatable = false)
    private UUID sourceConnectionId;

    @Column(name = "cipher_text", nullable = false)
    private String cipherText;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Column(name = "set_by_user_id", nullable = false)
    private UUID setByUserId;

    @Column(name = "set_at", nullable = false)
    private Instant setAt;

    protected SourceConnectionCredential() {
    }

    SourceConnectionCredential(
            UUID organizationId,
            UUID sourceConnectionId,
            EncryptedSecret secret,
            UUID setByUserId,
            Instant setAt) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.sourceConnectionId = sourceConnectionId;
        this.cipherText = secret.cipherText();
        this.keyVersion = secret.keyVersion();
        this.setByUserId = setByUserId;
        this.setAt = setAt;
    }

    void replaceWith(EncryptedSecret secret, UUID setByUserId, Instant setAt) {
        this.cipherText = secret.cipherText();
        this.keyVersion = secret.keyVersion();
        this.setByUserId = setByUserId;
        this.setAt = setAt;
    }

    EncryptedSecret stored() {
        return new EncryptedSecret(cipherText, keyVersion);
    }

    UUID getSetByUserId() {
        return setByUserId;
    }

    Instant getSetAt() {
        return setAt;
    }

    /** Deliberately omits the ciphertext, which is the only field worth keeping out of a log. */
    @Override
    public String toString() {
        return "SourceConnectionCredential[connection=" + sourceConnectionId
                + ", keyVersion=" + keyVersion + ", setAt=" + setAt + "]";
    }
}
