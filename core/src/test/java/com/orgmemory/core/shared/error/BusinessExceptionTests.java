package com.orgmemory.core.shared.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BusinessExceptionTests {

    @Test
    void retainsTransportNeutralPublicContractAndInternalCause() {
        var cause = new IllegalStateException("private persistence detail");
        var exception = new ExampleBusinessException(
                "Public safe detail", cause);

        assertEquals(
                BusinessErrorCategory.CONFLICT,
                exception.category());
        assertEquals("example.conflict", exception.code());
        assertEquals(
                BusinessErrorExposure.REQUEST_URI,
                exception.exposure());
        assertEquals("Public safe detail", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void rejectsUnstableOrBlankPublicContracts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new InvalidBusinessException(
                        "not dotted", "Safe detail"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new InvalidBusinessException(
                        "example.valid", "  "));
    }

    private static final class ExampleBusinessException
            extends BusinessException {

        private ExampleBusinessException(
                String publicMessage, Throwable cause) {
            super(
                    BusinessErrorCategory.CONFLICT,
                    "example.conflict",
                    publicMessage,
                    cause);
        }
    }

    private static final class InvalidBusinessException
            extends BusinessException {

        private InvalidBusinessException(
                String code, String publicMessage) {
            super(
                    BusinessErrorCategory.VALIDATION,
                    code,
                    publicMessage);
        }
    }
}
