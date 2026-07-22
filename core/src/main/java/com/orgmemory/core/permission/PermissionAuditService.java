package com.orgmemory.core.permission;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionAuditService {

    private final PermissionAuditStore store;

    PermissionAuditService(PermissionAuditStore store) {
        this.store = store;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID record(PermissionAuditCommand command) {
        return append(command);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAll(List<PermissionAuditCommand> commands) {
        Objects.requireNonNull(commands, "commands");
        commands.forEach(this::append);
    }

    private UUID append(PermissionAuditCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.organizationId(), "organizationId");
        Objects.requireNonNull(command.decision(), "decision");
        requireText(command.operation(), "operation");
        requireText(command.resourceType(), "resourceType");
        requireText(command.resourceId(), "resourceId");
        requireText(command.reasonCode(), "reasonCode");
        requireText(command.policyVersion(), "policyVersion");

        UUID eventId = UUID.randomUUID();
        store.append(new PermissionAuditEvent(
                eventId,
                command.organizationId(),
                command.actorUserId(),
                command.operation(),
                command.resourceType(),
                command.resourceId(),
                command.decision(),
                command.reasonCode(),
                command.policyVersion(),
                command.requestId(),
                fingerprint(command.queryText()),
                command.ingestionAclSnapshotId(),
                command.currentAclSnapshotId(),
                command.authorizationModelId(),
                command.sourceRevisionId(),
                command.knowledgeChunkId(),
                command.embeddingProfileId(),
                command.projectionGeneration(),
                Instant.now()));
        return eventId;
    }

    private static String fingerprint(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(queryText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
