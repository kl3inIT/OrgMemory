package com.orgmemory.graphrag.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.graphrag.chunking.ChunkerRegistry;
import com.orgmemory.graphrag.chunking.FixedTokenChunker;
import com.orgmemory.graphrag.parsing.DocumentParseRequest;
import com.orgmemory.graphrag.parsing.ParserRegistry;
import com.orgmemory.graphrag.parsing.ParserSpec;
import com.orgmemory.graphrag.parsing.PassthroughParser;
import com.orgmemory.graphrag.parsing.ReuseParser;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import com.orgmemory.graphrag.processing.ResolvedDocumentProcessingProfile;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProcessingRegistryConformanceTests {

    @Test
    void parserSnapshotDoesNotChangeWhenStartupRegistryChanges() {
        ParserRegistry registry = new ParserRegistry().register(new ParserSpec(
                PassthroughParser.COMPONENT,
                Set.of("txt"),
                true,
                true,
                "",
                new PassthroughParser()));
        var snapshot = registry.snapshot();

        registry.register(new ParserSpec(
                ReuseParser.COMPONENT,
                Set.of("canonical"),
                false,
                true,
                "",
                new ReuseParser()));

        assertEquals(1, snapshot.specs().size());
        assertThrows(IllegalArgumentException.class, () -> snapshot.require("reuse"));
        assertEquals(
                "Hello",
                snapshot.route("txt", null)
                        .parser()
                        .parse(new DocumentParseRequest(
                                "hello.txt",
                                "text/plain",
                                "Hello".getBytes(StandardCharsets.UTF_8),
                                Optional.empty()))
                        .document()
                        .content());
    }

    @Test
    void chunkerSnapshotDoesNotChangeWhenStartupRegistryChanges() {
        ChunkerRegistry registry = new ChunkerRegistry().register(new FixedTokenChunker());
        var snapshot = registry.snapshot();
        assertEquals(1, snapshot.chunkers().size());
        assertThrows(IllegalArgumentException.class, () -> snapshot.require("missing"));
    }

    @Test
    void resolvedProfileHashIsStableAcrossOptionInsertionOrder() {
        var component = new ProcessingComponentRef("fixture", "1");
        String sourceHash = ResolvedDocumentProcessingProfile.sha256("source");
        var left = ResolvedDocumentProcessingProfile.resolve(
                component,
                component,
                component,
                component,
                component,
                Optional.empty(),
                Map.of("requested.size", "1200", "actual.size", "600"),
                sourceHash);
        var rightOptions = new java.util.LinkedHashMap<String, String>();
        rightOptions.put("actual.size", "600");
        rightOptions.put("requested.size", "1200");
        var right = ResolvedDocumentProcessingProfile.resolve(
                component,
                component,
                component,
                component,
                component,
                Optional.empty(),
                rightOptions,
                sourceHash);

        assertEquals(left.canonicalForm(), right.canonicalForm());
        assertEquals(left.profileSha256(), right.profileSha256());
    }
}
