package com.orgmemory.api.assistant;

import com.orgmemory.core.assistant.AssistantUnavailableException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a failed turn into one short sentence the person who hit it can act on.
 *
 * <p>A failed turn used to end as the fixed frame {@link #GENERIC}, so an expired gateway
 * credential, a rate limit, a retired model and a broken deployment were indistinguishable to the
 * only person in a position to do something about them. {@code failure_code} and the {@code WARN}
 * line make a failure attributable to whoever operates the deployment; this makes it actionable to
 * whoever is sitting in front of it.
 *
 * <p><strong>Every returned sentence is a fixed string.</strong> Nothing is interpolated from the
 * failure, so a chatty or misconfigured gateway can never echo a key, a prompt fragment or provider
 * internals into the browser. That constraint is the reason this reads a status rather than a
 * message.
 *
 * <p>Saturation is read from the bounded {@code failureCode} carried on
 * {@link AssistantUnavailableException} rather than from a status, because it never had one: the
 * retrieval scheduler rejects the turn before any gateway is contacted. Everything else is keyed on
 * the leading HTTP status that clients put on the message ({@code "400: ..."} from the OpenAI SDK,
 * {@code "404 Not Found: ..."} from Spring) rather than on provider exception types, because the
 * provider SDK belongs to the AI integration module and the delivery layer only needs the status.
 */
final class AssistantStreamFailures {

    static final String GENERIC = "The assistant stream failed.";

    static final String BUSY =
            "The assistant is busy right now. Send the message again in a moment.";

    /** A leading three-digit status, as HTTP client exceptions render it. */
    private static final Pattern STATUS_PREFIX = Pattern.compile("^\\s*([45]\\d{2})(?::|\\s)");

    private static final int MAX_CAUSE_DEPTH = 10;

    private AssistantStreamFailures() {
    }

    static String describe(Throwable error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (cause instanceof AssistantUnavailableException unavailable
                    && AssistantRetrievalScheduler.REJECTED.equals(unavailable.failureCode())) {
                return BUSY;
            }
            int status = status(cause.getMessage());
            if (status > 0) {
                return forStatus(status);
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return GENERIC;
    }

    private static int status(String message) {
        if (message == null) {
            return 0;
        }
        Matcher matcher = STATUS_PREFIX.matcher(message);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    static String forStatus(int status) {
        return switch (status) {
            case 400, 422 -> "The selected model rejected this request. "
                    + "Pick a different chat model and send it again.";
            case 401, 403 -> "The AI gateway rejected its credentials. "
                    + "Ask an administrator to update its key.";
            case 404 -> "The selected model is no longer available on this gateway. "
                    + "Pick another model.";
            case 408, 504 -> "The AI gateway did not answer in time. Send the message again.";
            case 429 -> "The AI gateway is rate limiting requests. "
                    + "Send the message again in a moment.";
            default -> status >= 500
                    ? "The AI gateway failed while answering. Send the message again."
                    : "The AI gateway rejected this request. "
                            + "Ask an administrator to check the model and gateway.";
        };
    }
}
