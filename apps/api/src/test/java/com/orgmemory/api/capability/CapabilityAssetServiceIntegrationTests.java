package com.orgmemory.api.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.orgmemory.core.capability.AssetStatus;
import com.orgmemory.core.capability.AssetType;
import com.orgmemory.core.capability.AssetVersion;
import com.orgmemory.core.capability.AssetVisibility;
import com.orgmemory.core.capability.CapabilityAsset;
import com.orgmemory.core.capability.CapabilityAssetService;
import com.orgmemory.core.capability.CreateCapabilityAssetCommand;
import com.orgmemory.core.capability.RiskLevel;
import com.orgmemory.core.capability.UsageEventType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class CapabilityAssetServiceIntegrationTests {

    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SALES_DEPARTMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OWNER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID BACKUP_OWNER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    CapabilityAssetService assets;

    @Test
    void createAssetCreatesVersionAndCanTrackUsage() {
        CapabilityAsset asset = assets.create(new CreateCapabilityAssetCommand(
                ORGANIZATION_ID,
                SALES_DEPARTMENT_ID,
                "Proposal outline generator",
                "Creates a first-pass B2B proposal outline from discovery notes.",
                AssetType.PROMPT_TEMPLATE,
                "Proposal drafting",
                "Sales operations",
                "Claude",
                "sales, proposal",
                OWNER_ID,
                BACKUP_OWNER_ID,
                OWNER_ID,
                AssetVisibility.TEAM,
                RiskLevel.MEDIUM,
                "Create a proposal outline from {{notes}}.",
                "[{\"name\":\"Paste discovery notes\"},{\"name\":\"Generate outline\"}]",
                "{\"notes\":\"string\"}",
                "{\"outline\":\"string\"}",
                "Discovery call notes",
                "Executive summary, needs, proposal sections"));

        assertEquals(AssetStatus.DRAFT, asset.getStatus());
        List<AssetVersion> versions = assets.versions(asset.getId());
        assertEquals(1, versions.size());
        assertEquals(1, versions.getFirst().getVersionNumber());

        assertEquals(1, assets.recordUsage(asset.getId(), OWNER_ID, UsageEventType.USED, "{}"));
        assertFalse(assets.search(null, AssetType.PROMPT_TEMPLATE, "proposal").isEmpty());
    }

    @Test
    void reviewWorkflowMovesDraftToApproved() {
        CapabilityAsset asset = assets.create(new CreateCapabilityAssetCommand(
                ORGANIZATION_ID,
                SALES_DEPARTMENT_ID,
                "Meeting summary asset",
                "Summarizes customer meeting notes into decisions and next steps.",
                AssetType.WORKFLOW_AUTOMATION,
                "Meeting follow-up",
                "Customer success operations",
                "ChatGPT",
                "meeting, summary",
                OWNER_ID,
                BACKUP_OWNER_ID,
                OWNER_ID,
                AssetVisibility.TEAM,
                RiskLevel.LOW,
                "Summarize {{transcript}} into decisions and next steps.",
                null,
                null,
                null,
                "Transcript",
                "Decisions and next steps"));

        assertEquals(AssetStatus.IN_REVIEW, assets.submitForReview(asset.getId(), BACKUP_OWNER_ID, "Ready").getStatus());
        assertEquals(AssetStatus.APPROVED, assets.approve(asset.getId(), BACKUP_OWNER_ID, "Approved").getStatus());
    }
}
