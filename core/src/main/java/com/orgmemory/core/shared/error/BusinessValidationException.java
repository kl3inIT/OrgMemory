package com.orgmemory.core.shared.error;

/**
 * A caller-correctable use-case rejection with a stable domain code.
 *
 * <p>Use a dedicated domain exception when it carries behavior or a fixed
 * contract shared by many call sites. Use this type for narrow validations
 * where another exception class would only repeat category plumbing.
 */
public final class BusinessValidationException extends BusinessException {

    public BusinessValidationException(String code, String publicMessage) {
        super(BusinessErrorCategory.VALIDATION, code, publicMessage);
    }

    public BusinessValidationException(
            String code,
            String publicMessage,
            Throwable cause) {
        super(
                BusinessErrorCategory.VALIDATION,
                code,
                publicMessage,
                cause);
    }
}
