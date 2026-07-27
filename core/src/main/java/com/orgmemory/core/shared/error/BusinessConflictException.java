package com.orgmemory.core.shared.error;

/** A valid request that conflicts with the resource's current state. */
public final class BusinessConflictException extends BusinessException {

    public BusinessConflictException(String code, String publicMessage) {
        super(BusinessErrorCategory.CONFLICT, code, publicMessage);
    }

    public BusinessConflictException(
            String code,
            String publicMessage,
            Throwable cause) {
        super(
                BusinessErrorCategory.CONFLICT,
                code,
                publicMessage,
                cause);
    }
}
