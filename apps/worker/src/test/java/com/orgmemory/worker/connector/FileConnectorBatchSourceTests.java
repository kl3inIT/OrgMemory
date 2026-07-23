package com.orgmemory.worker.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.knowledge.ConnectorContractVersions;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Proves the committed crawl-batch fixtures are valid wire JSON that deserialize into the
 * connector contract, in filename order — the same path the live adapter's output must follow.
 */
class FileConnectorBatchSourceTests {

    @Test
    void readsCommittedFixturesInFilenameOrder() {
        FileConnectorBatchSource source = new FileConnectorBatchSource(
                new ConnectorCrawlProperties(false, null, ConnectorFixtures.directory().toString()));

        List<ConnectorCrawlBatch> batches = source.pendingBatches();

        assertEquals(3, batches.size());
        assertEquals("cursor-01-initial", batches.get(0).crawlCursor());
        assertEquals("cursor-02-recrawl", batches.get(1).crawlCursor());
        assertEquals("cursor-03-tombstone", batches.get(2).crawlCursor());

        ConnectorCrawlBatch initial = batches.get(0);
        assertEquals(ConnectorContractVersions.supported(), initial.versions());
        assertEquals("slack", initial.sourceSystem());
        assertEquals(3, initial.identities().size());
        assertEquals(1, initial.contents().size());
        assertEquals("C-general-msg", initial.contents().getFirst().externalObjectId());
        assertEquals(1, initial.permissions().size());

        ConnectorCrawlBatch recrawl = batches.get(1);
        assertTrue(recrawl.contents().isEmpty());
        assertEquals(1, recrawl.permissions().size());

        ConnectorCrawlBatch tombstone = batches.get(2);
        assertEquals(1, tombstone.tombstones().size());
        assertEquals("C-general-msg", tombstone.tombstones().getFirst().externalObjectId());
    }

    @Test
    void unsetDirectoryYieldsNoBatches() {
        FileConnectorBatchSource source = new FileConnectorBatchSource(
                new ConnectorCrawlProperties(false, null, ""));

        assertTrue(source.pendingBatches().isEmpty());
    }
}
