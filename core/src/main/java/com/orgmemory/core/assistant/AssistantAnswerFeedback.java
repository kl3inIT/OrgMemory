package com.orgmemory.core.assistant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assistant_answer_feedback")
class AssistantAnswerFeedback {

    @Id
    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssistantAnswerSentiment sentiment;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AssistantAnswerFeedback() {
    }

    AssistantAnswerFeedback(
            UUID messageId,
            UUID organizationId,
            UUID actorUserId,
            AssistantAnswerSentiment sentiment,
            Instant updatedAt) {
        this.messageId = Objects.requireNonNull(messageId, "messageId");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.sentiment = Objects.requireNonNull(sentiment, "sentiment");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    void update(AssistantAnswerSentiment nextSentiment, Instant timestamp) {
        sentiment = Objects.requireNonNull(nextSentiment, "nextSentiment");
        updatedAt = Objects.requireNonNull(timestamp, "timestamp");
    }

    AssistantAnswerFeedbackView view() {
        return new AssistantAnswerFeedbackView(messageId, sentiment, updatedAt);
    }

    AssistantAnswerSentiment sentiment() {
        return sentiment;
    }
}
