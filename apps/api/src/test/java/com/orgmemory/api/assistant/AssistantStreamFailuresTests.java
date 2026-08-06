package com.orgmemory.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.orgmemory.core.assistant.AssistantUnavailableException;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

/**
 * The sentence a failed turn ends on is the only thing the person who hit it can act on, and it is
 * also the last place a gateway's own words could reach a browser. These hold both ends: the
 * failure has to be named, and naming it must not quote anything the failure said.
 */
class AssistantStreamFailuresTests {

    @Test
    void namesSaturationFromTheFailureCodeRatherThanAStatus() {
        // The retrieval scheduler rejects before any gateway is contacted, so this failure never
        // had a status to read. Without the code it would fall through to the generic sentence and
        // tell a user to do nothing while the correct advice is to wait a moment.
        AssistantUnavailableException rejected = new AssistantUnavailableException(
                "The assistant is temporarily busy",
                new RejectedExecutionException("queue full"),
                AssistantRetrievalScheduler.REJECTED);

        assertThat(AssistantStreamFailures.describe(rejected))
                .isEqualTo(AssistantStreamFailures.BUSY);
    }

    @Test
    void distinguishesCredentialFailureFromRateLimitFromRetiredModel() {
        assertThat(AssistantStreamFailures.describe(new IllegalStateException("401: no key")))
                .contains("credentials");
        assertThat(AssistantStreamFailures.describe(new IllegalStateException("429 Too Many")))
                .contains("rate limiting");
        assertThat(AssistantStreamFailures.describe(new IllegalStateException("404 Not Found: x")))
                .contains("no longer available");
    }

    @Test
    void readsTheStatusThroughAWrappedCause() {
        Throwable wrapped = new IllegalStateException(
                "assistant failed",
                new IllegalStateException("503: upstream down"));

        assertThat(AssistantStreamFailures.describe(wrapped))
                .isEqualTo(AssistantStreamFailures.forStatus(503));
    }

    @Test
    void fallsBackToTheGenericSentenceWhenNothingIsRecognizable() {
        assertThat(AssistantStreamFailures.describe(new IllegalStateException("provider secret")))
                .isEqualTo(AssistantStreamFailures.GENERIC);
    }

    /**
     * A self-referential cause is not hypothetical: exception plumbing that re-wraps its own cause
     * produces one, and an unguarded walk would spin forever inside a streaming response.
     */
    @Test
    void terminatesOnASelfReferentialCauseChain() {
        Throwable looping = new IllegalStateException("no status here") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        assertThat(AssistantStreamFailures.describe(looping))
                .isEqualTo(AssistantStreamFailures.GENERIC);
    }

    @Test
    void neverQuotesTheFailureItDescribes() {
        String untrustedFailureDetail = "provider diagnostic containing a prompt fragment";

        assertThat(AssistantStreamFailures.describe(
                        new IllegalStateException(
                                "500: " + untrustedFailureDetail, new RuntimeException(untrustedFailureDetail))))
                .doesNotContain(untrustedFailureDetail)
                .doesNotContain("prompt fragment");
    }
}
