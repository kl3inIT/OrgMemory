package com.orgmemory.core.shared.error;

/** A temporary dependency or infrastructure failure safe to retry later. */
public final class BusinessUnavailableException extends BusinessException {

    public BusinessUnavailableException(String code, String publicMessage) {
        super(BusinessErrorCategory.UNAVAILABLE, code, publicMessage);
    }

    public BusinessUnavailableException(
            String code,
            String publicMessage,
            Throwable cause) {
        super(
                BusinessErrorCategory.UNAVAILABLE,
                code,
                publicMessage,
                cause);
    }
}
