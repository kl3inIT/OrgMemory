package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AssetPayloadDigesterTests {

    private final AssetPayloadDigester digester = new AssetPayloadDigester();

    @Test
    void equivalentJsonObjectsHaveTheSameCanonicalPayloadAndDigest() {
        var first = digester.canonicalize(
                "Triage ticket",
                "Classify and route a support request",
                "INTERNAL",
                "1",
                "{\"priority\":\"high\",\"rules\":{\"b\":2,\"a\":1}}");
        var reordered = digester.canonicalize(
                "Triage ticket",
                "Classify and route a support request",
                "INTERNAL",
                "1",
                "{\"rules\":{\"a\":1,\"b\":2},\"priority\":\"high\"}");

        assertEquals(first, reordered);
        assertEquals(
                "{\"priority\":\"high\",\"rules\":{\"a\":1,\"b\":2}}",
                first.payload());
    }

    @Test
    void oneByteSemanticChangeProducesADifferentDigest() {
        var first = digester.canonicalize(
                "Triage ticket", "Support", "INTERNAL", "1", "{\"limit\":5}");
        var changed = digester.canonicalize(
                "Triage ticket", "Support", "INTERNAL", "1", "{\"limit\":6}");

        assertNotEquals(first.digest(), changed.digest());
    }

    @Test
    void payloadMustBeAJsonObject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> digester.canonicalize(
                        "Triage ticket", "Support", "INTERNAL", "1", "[1,2,3]"));
    }
}
