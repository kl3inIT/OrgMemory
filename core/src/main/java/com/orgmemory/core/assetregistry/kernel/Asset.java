package com.orgmemory.core.assetregistry.kernel;

import com.orgmemory.core.assetregistry.api.AssetConflictException;
import com.orgmemory.core.assetregistry.api.AssetPortfolioState;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.UUID;

@Entity
@Table(name = "assets")
class Asset extends BaseEntity {

    private static final Pattern NAMESPACE =
            Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");
    private static final Pattern SLUG =
            Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 64, updatable = false)
    private AssetType type;

    @Column(nullable = false, length = 128, updatable = false)
    private String namespace;

    @Column(nullable = false, length = 128, updatable = false)
    private String slug;

    @Column(name = "knowledge_space_id", nullable = false, updatable = false)
    private UUID knowledgeSpaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "portfolio_state", nullable = false, length = 32)
    private AssetPortfolioState portfolioState;

    @Column(name = "authorization_ready", nullable = false)
    private boolean authorizationReady;

    protected Asset() {
    }

    Asset(
            UUID organizationId,
            AssetType type,
            String namespace,
            String slug,
            UUID knowledgeSpaceId) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.type = Objects.requireNonNull(type, "type");
        this.namespace = coordinate(namespace, NAMESPACE, "namespace");
        this.slug = coordinate(slug, SLUG, "slug");
        this.knowledgeSpaceId = Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        this.portfolioState = AssetPortfolioState.DRAFT_ONLY;
    }

    void markAuthorizationReady() {
        authorizationReady = true;
    }

    void activate() {
        if (portfolioState == AssetPortfolioState.RETIRED) {
            throw new AssetConflictException("A retired Asset cannot publish another release");
        }
        portfolioState = AssetPortfolioState.ACTIVE;
    }

    void startSunsetting() {
        if (portfolioState == AssetPortfolioState.ACTIVE) {
            portfolioState = AssetPortfolioState.SUNSETTING;
        }
    }

    void retire() {
        portfolioState = AssetPortfolioState.RETIRED;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    AssetType getType() {
        return type;
    }

    String getNamespace() {
        return namespace;
    }

    String getSlug() {
        return slug;
    }

    UUID getKnowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    AssetPortfolioState getPortfolioState() {
        return portfolioState;
    }

    boolean isAuthorizationReady() {
        return authorizationReady;
    }

    private static String coordinate(String value, Pattern format, String field) {
        String normalized = Objects.requireNonNull(value, field)
                .trim()
                .toLowerCase(Locale.ROOT);
        if (normalized.length() > 128 || !format.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    field + " must contain at most 128 lowercase characters "
                            + "separated by '.', '_', or '-'");
        }
        return normalized;
    }
}
