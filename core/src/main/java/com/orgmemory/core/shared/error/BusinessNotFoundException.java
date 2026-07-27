package com.orgmemory.core.shared.error;

/**
 * An expected missing-resource result.
 *
 * <p>Use {@link BusinessErrorExposure#OPAQUE_RESOURCE} whenever absence and
 * cross-tenant denial must remain indistinguishable.
 */
public final class BusinessNotFoundException extends BusinessException {

    public BusinessNotFoundException(String code, String publicMessage) {
        super(BusinessErrorCategory.NOT_FOUND, code, publicMessage);
    }

    public BusinessNotFoundException(
            String code,
            String publicMessage,
            BusinessErrorExposure exposure) {
        super(
                BusinessErrorCategory.NOT_FOUND,
                code,
                publicMessage,
                exposure);
    }
}
