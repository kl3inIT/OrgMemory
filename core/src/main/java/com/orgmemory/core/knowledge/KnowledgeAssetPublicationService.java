package com.orgmemory.core.knowledge;

import com.orgmemory.core.authorization.RelationshipTuple;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RelationshipTupleWriteRequest;
import com.orgmemory.core.authorization.RelationshipTupleWriteResult;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

@Service
public class KnowledgeAssetPublicationService {

    private final KnowledgeAssetPublicationCoordinator coordinator;
    private final RelationshipTupleWritePort relationshipTuples;

    KnowledgeAssetPublicationService(
            KnowledgeAssetPublicationCoordinator coordinator,
            RelationshipTupleWritePort relationshipTuples) {
        this.coordinator = coordinator;
        this.relationshipTuples = relationshipTuples;
    }

    public KnowledgeAssetRef publish(PublishKnowledgeAssetCommand command) {
        Objects.requireNonNull(command, "command");
        KnowledgeAssetPublicationState publication = coordinator.prepare(command);
        if (publication.applied()) {
            return coordinator.resolveApplied(
                    publication.organizationId(), publication.publicationId());
        }

        KnowledgeAssetPublicationState attempt = coordinator.startAttempt(
                publication.organizationId(), publication.publicationId());
        if (attempt.applied()) {
            return coordinator.resolveApplied(attempt.organizationId(), attempt.publicationId());
        }
        RelationshipTupleWriteResult result;
        try {
            result = Objects.requireNonNull(
                    relationshipTuples.write(new RelationshipTupleWriteRequest(List.of(
                            RelationshipTuple.of(
                                    "knowledge_space:" + attempt.knowledgeSpaceId(),
                                    "space",
                                    "knowledge_asset:" + attempt.knowledgeAssetId()),
                            RelationshipTuple.of(
                                    "user:" + attempt.ownerUserId(),
                                    "owner",
                                    "knowledge_asset:" + attempt.knowledgeAssetId())))),
                    "relationship tuple write result");
        } catch (RuntimeException exception) {
            coordinator.recordFailure(
                    attempt.organizationId(),
                    attempt.publicationId(),
                    "OPENFGA_WRITE_FAILED",
                    "The authorization relationship could not be applied");
            throw new KnowledgeAssetPublicationUnavailableException(
                    "Knowledge asset publication is waiting for authorization projection", exception);
        }
        if (!result.applied()) {
            coordinator.recordFailure(
                    attempt.organizationId(),
                    attempt.publicationId(),
                    result.reasonCode(),
                    "The authorization relationship could not be confirmed");
            throw new KnowledgeAssetPublicationUnavailableException(
                    "Knowledge asset publication is waiting for authorization projection");
        }
        return coordinator.complete(
                attempt.organizationId(), attempt.publicationId(), result.policyVersion());
    }
}
