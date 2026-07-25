package com.orgmemory.core.assistant;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assistant_conversations")
class AssistantConversation extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    protected AssistantConversation() {
    }

    AssistantConversation(
            UUID id,
            UUID organizationId,
            UUID actorUserId,
            String title,
            Instant timestamp) {
        super(Objects.requireNonNull(id, "id"));
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.title = normalizeTitle(title);
        this.lastActivityAt = Objects.requireNonNull(timestamp, "timestamp");
    }

    void rename(String nextTitle) {
        title = normalizeTitle(nextTitle);
    }

    void touch(Instant timestamp) {
        lastActivityAt = Objects.requireNonNull(timestamp, "timestamp");
    }

    UUID organizationId() {
        return organizationId;
    }

    UUID actorUserId() {
        return actorUserId;
    }

    String title() {
        return title;
    }

    Instant lastActivityAt() {
        return lastActivityAt;
    }

    private static String normalizeTitle(String value) {
        String normalized = Objects.requireNonNull(value, "title").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Conversation title is required");
        }
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 117) + "...";
    }
}
