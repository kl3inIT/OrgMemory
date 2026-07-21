package com.orgmemory.core.organization;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(name = "external_identities", uniqueConstraints = {
        @UniqueConstraint(name = "uq_external_identity_issuer_subject", columnNames = {"issuer", "subject"}),
        @UniqueConstraint(name = "uq_external_identity_user_issuer", columnNames = {"app_user_id", "issuer"})
})
public class ExternalIdentity extends BaseEntity {

    @Column(name = "app_user_id", nullable = false)
    private UUID appUserId;

    @Column(nullable = false, length = 512)
    private String issuer;

    @Column(nullable = false)
    private String subject;

    protected ExternalIdentity() {
    }

    public ExternalIdentity(UUID appUserId, String issuer, String subject) {
        super(UUID.randomUUID());
        this.appUserId = appUserId;
        this.issuer = issuer;
        this.subject = subject;
    }

    public UUID getAppUserId() {
        return appUserId;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getSubject() {
        return subject;
    }

}
