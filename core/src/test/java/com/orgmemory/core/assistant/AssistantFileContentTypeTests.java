package com.orgmemory.core.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.core.shared.error.BusinessValidationException;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class AssistantFileContentTypeTests {

    @Test
    void detectsPdfContentInsteadOfTrustingTheBrowserMediaType() {
        var type = AssistantFileContentType.require("policy.pdf", 8);
        AssistantFileContentType.validateSignature(
                type, new ByteArrayInputStream("%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));

        assertEquals("application/pdf", type.mediaType());
        assertThrows(
                BusinessValidationException.class,
                () -> AssistantFileContentType.validateSignature(
                        type, new ByteArrayInputStream("not-pdf!".getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
    }

    @Test
    void keepsImagesAndStandaloneArchivesOutsideTheClosedScope() {
        assertThrows(
                BusinessValidationException.class,
                () -> AssistantFileContentType.require("photo.png", 10));
        assertThrows(
                BusinessValidationException.class,
                () -> AssistantFileContentType.require("bundle.zip", 10));
    }

    @Test
    void acceptsOfficeZipContainersWithoutOpeningArchiveUploadScope() {
        var type = AssistantFileContentType.require("policy.docx", 4);
        AssistantFileContentType.validateSignature(
                type, new ByteArrayInputStream(new byte[] {0x50, 0x4b, 0x03, 0x04}));
        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                type.mediaType());
    }

    @Test
    void derivesOnlyThePortableBaseName() {
        assertEquals("policy.pdf", AssistantFileContentType.safeFileName("C:\\Users\\owner\\policy.pdf"));
        assertEquals("policy.pdf", AssistantFileContentType.safeFileName("/tmp/policy.pdf"));
    }

    @Test
    void servesActiveTextFormatsAsPlainAttachmentContent() {
        var html = AssistantFileContentType.require("policy.html", 10);
        assertEquals("text/html", html.mediaType());
        assertEquals("text/plain", html.browserSafeMediaType());
        assertFalse(html.inlinePreviewAllowed());
    }
}
