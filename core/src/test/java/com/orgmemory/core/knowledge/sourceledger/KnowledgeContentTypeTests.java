package com.orgmemory.core.knowledge.sourceledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KnowledgeContentTypeTests {

    @Test
    void keepsCanonicalAndBrowserSafeMarkdownTypesExplicit() {
        KnowledgeContentType markdown =
                KnowledgeContentType.fromFileName("policy.MD").orElseThrow();

        assertEquals("text/markdown", markdown.canonicalMediaType());
        assertEquals("text/plain", markdown.browserSafeMediaType());
        assertTrue(markdown.uploadAllowed());
        assertTrue(markdown.inlinePreviewAllowed());
    }

    @Test
    void recognizesPreviewOnlyImagesWithoutAllowingImageUploads() {
        KnowledgeContentType image =
                KnowledgeContentType.fromFileName("evidence.jpeg").orElseThrow();

        assertEquals("image/jpeg", image.browserSafeMediaType());
        assertFalse(image.uploadAllowed());
        assertTrue(image.inlinePreviewAllowed());
    }
}
