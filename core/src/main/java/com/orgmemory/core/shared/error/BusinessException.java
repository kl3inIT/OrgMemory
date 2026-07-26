package com.orgmemory.core.shared.error;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A use-case failure that is safe for a delivery adapter to disclose.
 *
 * <p>The stable code is machine-readable, while {@link #getMessage()} is the
 * deliberately public detail. Internal causes remain available for logs but
 * are never part of the public contract.
 */
public abstract class BusinessException extends RuntimeException {

    private static final Pattern CODE =
            Pattern.compile("[a-z][a-z0-9-]*(?:\\.[a-z0-9][a-z0-9-]*)+");

    private final BusinessErrorCategory category;
    private final String code;
    private final BusinessErrorExposure exposure;

    protected BusinessException(
            BusinessErrorCategory category,
            String code,
            String publicMessage) {
        this(category, code, publicMessage, (Throwable) null);
    }

    protected BusinessException(
            BusinessErrorCategory category,
            String code,
            String publicMessage,
            BusinessErrorExposure exposure) {
        this(category, code, publicMessage, exposure, null);
    }

    protected BusinessException(
            BusinessErrorCategory category,
            String code,
            String publicMessage,
            Throwable cause) {
        this(
                category,
                code,
                publicMessage,
                BusinessErrorExposure.REQUEST_URI,
                cause);
    }

    protected BusinessException(
            BusinessErrorCategory category,
            String code,
            String publicMessage,
            BusinessErrorExposure exposure,
            Throwable cause) {
        super(requireMessage(publicMessage), cause);
        this.category = Objects.requireNonNull(category, "category");
        this.code = requireCode(code);
        this.exposure = Objects.requireNonNull(exposure, "exposure");
    }

    public final BusinessErrorCategory category() {
        return category;
    }

    public final String code() {
        return code;
    }

    public final BusinessErrorExposure exposure() {
        return exposure;
    }

    private static String requireCode(String value) {
        String normalized = Objects.requireNonNull(value, "code").strip();
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Business error code must be a dotted lowercase identifier");
        }
        return normalized;
    }

    private static String requireMessage(String value) {
        String normalized = Objects.requireNonNull(value, "publicMessage").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Business error public message is required");
        }
        return normalized;
    }
}
