package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.ConnectorIdentityResolution.ResolvedPrincipal;
import jakarta.persistence.EntityManager;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Captures, seals, and atomically activates source group membership independently of ACLs. */
@Service
class SourceGroupMembershipService {

    private final SourceMembershipSyncRunRepository syncRuns;
    private final SourceGroupMembershipSnapshotRepository snapshots;
    private final SourceGroupMembershipMemberRepository members;
    private final SourceGroupMembershipSnapshotSealRepository seals;
    private final SourceGroupMembershipHeadRepository heads;
    private final EntityManager entityManager;

    SourceGroupMembershipService(
            SourceMembershipSyncRunRepository syncRuns,
            SourceGroupMembershipSnapshotRepository snapshots,
            SourceGroupMembershipMemberRepository members,
            SourceGroupMembershipSnapshotSealRepository seals,
            SourceGroupMembershipHeadRepository heads,
            EntityManager entityManager) {
        this.syncRuns = syncRuns;
        this.snapshots = snapshots;
        this.members = members;
        this.seals = seals;
        this.heads = heads;
        this.entityManager = entityManager;
    }

    @Transactional
    void reconcile(
            ConnectorIngestionContext ctx,
            List<ConnectorMembershipItem> capturedMemberships,
            ConnectorIdentityResolution identities) {
        if (capturedMemberships.isEmpty()) {
            return;
        }
        Instant capturedAt = Instant.now();
        SourceMembershipSyncRun run = syncRuns.saveAndFlush(new SourceMembershipSyncRun(
                ctx.organizationId(),
                ctx.sourceSystem(),
                ctx.sourceConnectionKey(),
                capturedAt));
        Set<String> seenGroups = new HashSet<>();
        for (ConnectorMembershipItem captured : capturedMemberships) {
            if (!seenGroups.add(captured.groupNativePrincipalId())) {
                throw new IllegalArgumentException(
                        "connector membership repeats group "
                                + captured.groupNativePrincipalId());
            }
            reconcileGroup(ctx, run, captured, identities, capturedAt);
        }
    }

    private void reconcileGroup(
            ConnectorIngestionContext ctx,
            SourceMembershipSyncRun run,
            ConnectorMembershipItem captured,
            ConnectorIdentityResolution identities,
            Instant capturedAt) {
        ResolvedPrincipal group = requirePrincipal(
                identities,
                captured.groupNativePrincipalId(),
                SourcePrincipalKind.SOURCE_GROUP,
                "membership group");
        List<ResolvedMember> resolvedMembers = captured.members().stream()
                .map(member -> {
                    if (member.kind() == SourcePrincipalKind.SOURCE_GROUP) {
                        throw new IllegalArgumentException(
                                "nested source group membership is not supported by this build");
                    }
                    ResolvedPrincipal principal = requirePrincipal(
                            identities,
                            member.nativePrincipalId(),
                            member.kind(),
                            "membership member");
                    return new ResolvedMember(principal.id(), principal.kind());
                })
                .distinct()
                .sorted(Comparator.comparing((ResolvedMember member) -> member.kind().name())
                        .thenComparing(member -> member.id().toString()))
                .toList();

        acquireTransactionLock(ctx.organizationId(), group.id());
        SourceGroupMembershipHead head = heads
                .findByOrganizationIdAndGroupPrincipalId(ctx.organizationId(), group.id())
                .orElse(null);
        String membersSha = sha256(canonicalMembers(resolvedMembers));
        if (captured.captureStatus() == MembershipCaptureStatus.COMPLETE
                && head != null
                && seals.findByMembershipSnapshotId(head.getCurrentSnapshotId())
                        .map(SourceGroupMembershipSnapshotSeal::getMembersSha256)
                        .filter(membersSha::equals)
                        .isPresent()) {
            return;
        }

        long generation = snapshots
                .findFirstByOrganizationIdAndGroupPrincipalIdOrderByMembershipGenerationDesc(
                        ctx.organizationId(), group.id())
                .map(snapshot -> snapshot.getMembershipGeneration() + 1)
                .orElse(1L);
        SourceGroupMembershipSnapshot snapshot = snapshots.saveAndFlush(
                new SourceGroupMembershipSnapshot(
                        ctx.organizationId(),
                        run.getId(),
                        group.id(),
                        generation,
                        captured.captureStatus(),
                        captured.incompleteReason(),
                        capturedAt));
        members.saveAllAndFlush(resolvedMembers.stream()
                .map(member -> new SourceGroupMembershipMember(
                        ctx.organizationId(),
                        snapshot.getId(),
                        member.id(),
                        member.kind(),
                        capturedAt))
                .toList());
        if (captured.captureStatus() != MembershipCaptureStatus.COMPLETE) {
            return;
        }
        seals.saveAndFlush(new SourceGroupMembershipSnapshotSeal(
                snapshot,
                resolvedMembers.size(),
                membersSha,
                capturedAt));
        if (head == null) {
            heads.saveAndFlush(new SourceGroupMembershipHead(snapshot, capturedAt));
        } else {
            head.advance(snapshot, capturedAt);
            heads.saveAndFlush(head);
        }
    }

    private static ResolvedPrincipal requirePrincipal(
            ConnectorIdentityResolution identities,
            String nativePrincipalId,
            SourcePrincipalKind expectedKind,
            String role) {
        ResolvedPrincipal principal = identities.find(expectedKind, nativePrincipalId);
        if (principal == null) {
            throw new IllegalArgumentException(
                    role + " is not an observed " + expectedKind + ": " + nativePrincipalId);
        }
        return principal;
    }

    private void acquireTransactionLock(UUID organizationId, UUID groupPrincipalId) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(("source-membership|" + organizationId + "|" + groupPrincipalId)
                            .getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(?1)")
                .setParameter(1, ByteBuffer.wrap(digest).getLong())
                .getSingleResult();
    }

    private static String canonicalMembers(List<ResolvedMember> members) {
        return members.stream()
                .map(member -> member.kind() + ":" + member.id())
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ResolvedMember(UUID id, SourcePrincipalKind kind) {
    }
}
