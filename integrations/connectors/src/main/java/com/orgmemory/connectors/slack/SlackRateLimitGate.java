package com.orgmemory.connectors.slack;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;

/**
 * Holds back requests until a rate limit Slack imposed has expired.
 *
 * <p>The obvious implementation — sleep for {@code Retry-After}, then retry that one call — is
 * not enough, because Slack rate limits a workspace and a method, not a caller. Every other
 * request in flight is about to be told the same thing. So the delay is recorded once and
 * every request waits it out <em>before</em> being sent, rather than each discovering the limit
 * for itself. Onyx learned this the expensive way and shares the deadline through Redis; a
 * single worker process needs only this, and a deployment that runs more than one will need
 * the deadline to move somewhere shared.
 *
 * <p>The wait carries jitter so that requests held behind one limit do not resume in lockstep
 * and immediately earn another.
 */
final class SlackRateLimitGate {

    private static final Duration MAX_WAIT = Duration.ofMinutes(2);
    private static final double JITTER_FRACTION = 0.25;

    private final AtomicLong openAtMillis = new AtomicLong();
    private final RandomGenerator random;
    private final Sleeper sleeper;

    SlackRateLimitGate() {
        this(RandomGenerator.getDefault(), Thread::sleep);
    }

    SlackRateLimitGate(RandomGenerator random, Sleeper sleeper) {
        this.random = random;
        this.sleeper = sleeper;
    }

    /** Blocks until any recorded limit has passed. Called before every request. */
    void awaitOpen() {
        long waitMillis = openAtMillis.get() - System.currentTimeMillis();
        if (waitMillis <= 0) {
            return;
        }
        sleep(Math.min(waitMillis, MAX_WAIT.toMillis()));
    }

    /**
     * Records a limit Slack just reported. The deadline only ever moves outwards, so a shorter
     * {@code Retry-After} arriving late cannot shorten a longer wait already in force.
     */
    void closeFor(Duration retryAfter) {
        long jittered = (long) (retryAfter.toMillis() * (1 + JITTER_FRACTION * random.nextDouble()));
        long deadline = System.currentTimeMillis() + Math.min(jittered, MAX_WAIT.toMillis());
        openAtMillis.accumulateAndGet(deadline, Math::max);
    }

    private void sleep(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SlackApiException("Interrupted while waiting out a Slack rate limit");
        }
    }

    /** Seam so tests can prove the waiting without spending the time. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
