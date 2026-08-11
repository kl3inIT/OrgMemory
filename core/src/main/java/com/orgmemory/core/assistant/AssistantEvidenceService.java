package com.orgmemory.core.assistant;

import com.orgmemory.core.authorization.EffectiveAuthorizationService;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.search.KnowledgeEvidenceSelection;
import com.orgmemory.core.knowledge.evidence.GovernedEvidenceQuery;
import com.orgmemory.core.knowledge.evidence.GovernedEvidenceRef;
import com.orgmemory.core.knowledge.evidence.GovernedFileUploadResult;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.error.BusinessConflictException;
import com.orgmemory.core.shared.error.BusinessErrorExposure;
import com.orgmemory.core.shared.error.BusinessNotFoundException;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantEvidenceService {

    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");
    private static final String ASSET_TYPE = "knowledge_asset";

    private final AssistantConversationRepository conversations;
    private final AssistantEvidenceBindingRepository bindings;
    private final AssistantTurnEvidenceBindingRepository turnBindings;
    private final GovernedEvidenceQuery sources;
    private final EffectiveAuthorizationService authorization;
    private final AssistantEvidenceAnswerabilityPort answerability;
    private final Clock clock;

    AssistantEvidenceService(
            AssistantConversationRepository conversations,
            AssistantEvidenceBindingRepository bindings,
            AssistantTurnEvidenceBindingRepository turnBindings,
            GovernedEvidenceQuery sources,
            EffectiveAuthorizationService authorization,
            AssistantEvidenceAnswerabilityPort answerability,
            Clock clock) {
        this.conversations = conversations;
        this.bindings = bindings;
        this.turnBindings = turnBindings;
        this.sources = sources;
        this.authorization = authorization;
        this.answerability = answerability;
        this.clock = clock;
    }

    @Transactional
    public AssistantEvidenceBindingView bindUploaded(
            CurrentActor actor,
            UUID requestedConversationId,
            GovernedFileUploadResult upload) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(upload, "upload");
        Instant now = clock.instant();
        AssistantConversation conversation;
        if (requestedConversationId == null) {
            conversation = conversations.save(new AssistantConversation(
                    UUID.randomUUID(),
                    actor.organizationId(),
                    actor.userId(),
                    upload.fileName(),
                    now));
        } else {
            conversation = conversations
                    .findForUpdateByIdAndOrganizationIdAndActorUserId(
                            requestedConversationId,
                            actor.organizationId(),
                            actor.userId())
                    .orElseThrow(AssistantEvidenceService::notFound);
            conversation.touch(now);
        }
        AssistantEvidenceBinding binding = bindings.save(new AssistantEvidenceBinding(
                UUID.randomUUID(),
                actor.organizationId(),
                conversation.getId(),
                actor.userId(),
                upload.sourceObjectId(),
                upload.sourceRevisionId()));
        return evaluate(actor, binding);
    }

    @Transactional(readOnly = true)
    public AssistantEvidenceBindingView get(
            CurrentActor actor,
            UUID conversationId,
            UUID bindingId) {
        requireConversation(actor, conversationId);
        return evaluate(actor, requireBinding(actor, conversationId, bindingId));
    }

    @Transactional(readOnly = true)
    public List<AssistantEvidenceBindingView> list(
            CurrentActor actor,
            UUID conversationId) {
        requireConversation(actor, conversationId);
        return bindings
                .findAllByOrganizationIdAndConversationIdAndCreatedByUserIdOrderByCreatedAtAsc(
                        actor.organizationId(),
                        conversationId,
                        actor.userId())
                .stream()
                .map(binding -> evaluate(actor, binding))
                .toList();
    }

    void requireUploadConversation(
            CurrentActor actor,
            UUID conversationId) {
        if (conversationId != null) {
            requireConversation(actor, conversationId);
        }
    }

    @Transactional
    KnowledgeEvidenceSelection claimForTurn(
            CurrentActor actor,
            UUID conversationId,
            UUID turnId,
            UUID userMessageId,
            List<UUID> requestedBindingIds) {
        List<UUID> bindingIds = requestedBindingIds == null
                ? List.of()
                : List.copyOf(requestedBindingIds);
        if (bindingIds.isEmpty()) {
            return KnowledgeEvidenceSelection.unrestricted();
        }
        if (bindingIds.size() > KnowledgeEvidenceSelection.MAXIMUM_ITEMS
                || new LinkedHashSet<>(bindingIds).size() != bindingIds.size()) {
            throw new BusinessValidationException(
                    "assistant.evidence-selection-invalid",
                    "Select between one and three distinct files");
        }
        List<AssistantEvidenceBinding> found = bindings
                .findAllByIdInAndOrganizationIdAndConversationIdAndCreatedByUserId(
                        bindingIds,
                        actor.organizationId(),
                        conversationId,
                        actor.userId());
        Map<UUID, AssistantEvidenceBinding> byId = new LinkedHashMap<>();
        found.forEach(binding -> byId.put(binding.getId(), binding));
        if (byId.size() != bindingIds.size()) {
            throw notFound();
        }

        java.util.ArrayList<KnowledgeEvidenceSelection.Item> items = new java.util.ArrayList<>();
        java.util.ArrayList<AssistantTurnEvidenceBinding> claims = new java.util.ArrayList<>();
        for (int index = 0; index < bindingIds.size(); index++) {
            AssistantEvidenceBinding binding = byId.get(bindingIds.get(index));
            Evaluation evaluation = evaluation(actor, binding);
            if (evaluation.view().status() != AssistantEvidenceStatus.READY
                    || evaluation.source() == null) {
                throw new BusinessConflictException(
                        "assistant.evidence-not-ready",
                        "Selected file evidence is not ready");
            }
            GovernedEvidenceRef source = evaluation.source();
            items.add(new KnowledgeEvidenceSelection.Item(
                    binding.getId(),
                    source.sourceObjectId(),
                    source.sourceRevisionId(),
                    source.knowledgeAssetId()));
            claims.add(new AssistantTurnEvidenceBinding(
                    UUID.randomUUID(),
                    turnId,
                    userMessageId,
                    binding.getId(),
                    actor.organizationId(),
                    conversationId,
                    actor.userId(),
                    index + 1));
        }
        turnBindings.saveAll(claims);
        return KnowledgeEvidenceSelection.restricted(items);
    }

    private Evaluation evaluation(CurrentActor actor, AssistantEvidenceBinding binding) {
        GovernedEvidenceRef source = sources.find(
                        actor.organizationId(),
                        binding.sourceObjectId(),
                        binding.sourceRevisionId())
                .orElse(null);
        if (source == null) {
            return unavailable(binding, null);
        }
        if (source.knowledgeAssetId() != null
                && !authorization.authorize(
                                actor.organizationId(),
                                actor.principal(),
                                CAN_VIEW,
                                ResourceRef.of(
                                        actor.organizationId(),
                                        ASSET_TYPE,
                                        source.knowledgeAssetId()))
                        .allowed()) {
            return unavailable(binding, null);
        }
        // Before publication there is no Knowledge Asset to authorize. The binding lookup is
        // actor-scoped, so its uploader may see the processing metadata until an asset exists.
        AssistantEvidenceStatus status;
        String failureCode = source.failureCode();
        if (!source.sourceActive() || !source.latestRevision()) {
            status = AssistantEvidenceStatus.UNAVAILABLE;
        } else if (source.processingState() == GovernedEvidenceRef.ProcessingState.FAILED
                || source.processingState() == GovernedEvidenceRef.ProcessingState.QUARANTINED) {
            status = AssistantEvidenceStatus.FAILED;
        } else if (source.processingState() != GovernedEvidenceRef.ProcessingState.READY
                || !source.currentRevision()
                || source.knowledgeAssetId() == null) {
            status = AssistantEvidenceStatus.PROCESSING;
        } else {
            AssistantEvidenceAnswerabilityPort.Answerability engine = answerability.evaluate(source);
            status = switch (engine.state()) {
                case INDEXING -> AssistantEvidenceStatus.INDEXING;
                case READY -> AssistantEvidenceStatus.READY;
                case FAILED -> AssistantEvidenceStatus.FAILED;
            };
            failureCode = engine.failureCode();
        }
        return new Evaluation(view(binding, source, status, failureCode), source);
    }

    private AssistantEvidenceBindingView evaluate(
            CurrentActor actor,
            AssistantEvidenceBinding binding) {
        return evaluation(actor, binding).view();
    }

    private static Evaluation unavailable(
            AssistantEvidenceBinding binding,
            GovernedEvidenceRef source) {
        return new Evaluation(
                view(
                        binding,
                        source,
                        AssistantEvidenceStatus.UNAVAILABLE,
                        null),
                source);
    }

    private static AssistantEvidenceBindingView view(
            AssistantEvidenceBinding binding,
            GovernedEvidenceRef source,
            AssistantEvidenceStatus status,
            String failureCode) {
        return new AssistantEvidenceBindingView(
                binding.getId(),
                binding.conversationId(),
                binding.sourceObjectId(),
                binding.sourceRevisionId(),
                source == null ? null : source.knowledgeAssetId(),
                source == null ? "Unavailable evidence" : source.title(),
                source == null ? "Unavailable file" : source.fileName(),
                status,
                failureCode,
                binding.getCreatedAt());
    }

    private AssistantEvidenceBinding requireBinding(
            CurrentActor actor,
            UUID conversationId,
            UUID bindingId) {
        return bindings.findByIdAndOrganizationIdAndConversationIdAndCreatedByUserId(
                        Objects.requireNonNull(bindingId, "bindingId"),
                        actor.organizationId(),
                        Objects.requireNonNull(conversationId, "conversationId"),
                        actor.userId())
                .orElseThrow(AssistantEvidenceService::notFound);
    }

    private void requireConversation(CurrentActor actor, UUID conversationId) {
        conversations.findByIdAndOrganizationIdAndActorUserId(
                        Objects.requireNonNull(conversationId, "conversationId"),
                        actor.organizationId(),
                        actor.userId())
                .orElseThrow(AssistantEvidenceService::notFound);
    }

    private static BusinessNotFoundException notFound() {
        return new BusinessNotFoundException(
                "assistant.evidence-not-found",
                "Assistant evidence not found",
                BusinessErrorExposure.OPAQUE_RESOURCE);
    }

    private record Evaluation(
            AssistantEvidenceBindingView view,
            GovernedEvidenceRef source) {
    }
}
