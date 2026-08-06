package com.orgmemory.api.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.sourceledger.CreateUploadSourceCommand;
import com.orgmemory.core.knowledge.sourceledger.SourceActionAuthorizationPort;
import com.orgmemory.core.knowledge.sourceledger.SourceListCommand;
import com.orgmemory.core.knowledge.sourceledger.SourceListStatus;
import com.orgmemory.core.knowledge.sourceledger.SourceQueryService;
import com.orgmemory.core.knowledge.sourceledger.SourceSummary;
import com.orgmemory.core.knowledge.sourceledger.SourceSummaryPage;
import com.orgmemory.core.knowledge.sourceledger.SourceUploadService;
import com.orgmemory.core.knowledge.sourceledger.SourceVisibilityPort;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceService;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceTarget;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.ObjectWriteRequest;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Sql("/db/test-foundation.sql")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SourceListingIntegrationTests {

    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEPARTMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID OTHER_USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID SALES_SPACE_ID = UUID.fromString("88888888-8888-4888-8888-888888888802");
    private static final CurrentActor ACTOR = actor(USER_ID, "Linh Nguyen");
    private static final CurrentActor OTHER_ACTOR = actor(OTHER_USER_ID, "Minh Tran");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @MockitoBean
    ObjectStoragePort objects;

    @MockitoBean
    KnowledgeSpaceService knowledgeSpaces;

    @MockitoBean
    SourceVisibilityPort visibility;

    @MockitoBean
    SourceActionAuthorizationPort actions;

    @Autowired
    SourceUploadService uploads;

    @Autowired
    SourceQueryService sources;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    @BeforeEach
    void configurePorts() {
        when(visibility.visibleSourceObjectIds(any())).thenReturn(List.of());
        when(visibility.maximumAuthorizedObjects()).thenReturn(5_000);
        when(actions.deletableKnowledgeAssetIds(any())).thenReturn(Set.of());
        when(knowledgeSpaces.requireUploadTarget(any(), eq(SALES_SPACE_ID))).thenReturn(
                new KnowledgeSpaceTarget(
                        SALES_SPACE_ID,
                        "sales",
                        "Sales Knowledge",
                        DEPARTMENT_ID));
        when(objects.put(any(), any())).thenAnswer(invocation -> {
            ObjectWriteRequest request = invocation.getArgument(0);
            return new StoredObject(
                    request.key(),
                    request.contentLength(),
                    request.mediaType(),
                    "0".repeat(64),
                    "listing-test",
                    null);
        });
    }

    @Test
    void latestRevisionKeysetKeepsStableRowsVisibleAcrossAConcurrentMove() {
        SourceSummary newest = upload(ACTOR, "newest.txt");
        SourceSummary stableSecond = upload(ACTOR, "stable-second.txt");
        SourceSummary moving = upload(ACTOR, "moving.txt");
        SourceSummary stableLast = upload(ACTOR, "stable-last.txt");
        entityManager.flush();
        setRevisionUpdatedAt(newest.id(), Instant.parse("2026-08-06T04:00:00Z"));
        setRevisionUpdatedAt(stableSecond.id(), Instant.parse("2026-08-06T03:00:00Z"));
        setRevisionUpdatedAt(moving.id(), Instant.parse("2026-08-06T02:00:00Z"));
        setRevisionUpdatedAt(stableLast.id(), Instant.parse("2026-08-06T01:00:00Z"));
        entityManager.clear();

        SourceSummaryPage first = sources.listVisible(
                ACTOR,
                new SourceListCommand(
                        SALES_SPACE_ID,
                        KnowledgeClassification.INTERNAL,
                        SourceListStatus.PROCESSING,
                        null,
                        null,
                        2));

        assertEquals(List.of(newest.id(), stableSecond.id()), ids(first));
        assertEquals(4, first.total());
        assertEquals(4, first.statusCounts().processing());
        assertNotNull(first.nextCursor());
        assertFalse(jdbc.queryForObject(
                "SELECT current_revision_id IS NOT NULL FROM source_objects WHERE id = ?",
                Boolean.class,
                stableSecond.id()));

        setRevisionUpdatedAt(moving.id(), Instant.parse("2026-08-06T05:00:00Z"));
        entityManager.clear();

        SourceSummaryPage second = sources.listVisible(
                ACTOR,
                new SourceListCommand(
                        SALES_SPACE_ID,
                        KnowledgeClassification.INTERNAL,
                        SourceListStatus.PROCESSING,
                        null,
                        first.nextCursor(),
                        2));

        assertEquals(List.of(stableLast.id()), ids(second));
        assertEquals(4, second.total());
    }

    @Test
    void filteringByASpaceOutsideTheActorsAuthorizedIdsReturnsAnEmptyPage() {
        SourceSummary otherUsersSource = upload(OTHER_ACTOR, "private-plan.txt");

        SourceSummaryPage page = sources.listVisible(
                ACTOR,
                new SourceListCommand(
                        SALES_SPACE_ID,
                        KnowledgeClassification.INTERNAL,
                        null,
                        null,
                        null,
                        25));

        assertEquals(List.of(), ids(page));
        assertFalse(page.items().stream().anyMatch(item -> item.id().equals(otherUsersSource.id())));
    }

    private SourceSummary upload(CurrentActor actor, String fileName) {
        byte[] content = fileName.getBytes(StandardCharsets.UTF_8);
        return uploads.upload(
                new CreateUploadSourceCommand(
                        actor,
                        fileName,
                        content.length,
                        KnowledgeClassification.INTERNAL,
                        SALES_SPACE_ID),
                new ByteArrayInputStream(content));
    }

    private void setRevisionUpdatedAt(UUID sourceId, Instant updatedAt) {
        jdbc.update(
                "UPDATE source_revisions SET updated_at = ? WHERE source_object_id = ?",
                Timestamp.from(updatedAt),
                sourceId);
    }

    private static List<UUID> ids(SourceSummaryPage page) {
        return page.items().stream().map(SourceSummary::id).toList();
    }

    private static CurrentActor actor(UUID userId, String name) {
        return new CurrentActor(
                userId,
                ORGANIZATION_ID,
                DEPARTMENT_ID,
                name,
                name.toLowerCase().replace(' ', '.') + "@example.test");
    }
}
