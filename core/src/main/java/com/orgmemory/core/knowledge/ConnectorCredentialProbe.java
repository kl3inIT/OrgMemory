package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.secret.SecretValue;

/**
 * Answers what a credential actually is, before anything is stored or crawled.
 *
 * <p>An adapter contributes one of these beside its {@link ConnectorSourceProfile}. The
 * administration API asks the registry rather than any source by name, which is what lets a
 * second connector arrive without an endpoint changing.
 *
 * <p>A probe is total. A refusal, a revoked credential and an unreachable source are all
 * results rather than exceptions, because the caller asked a question whose negative answer is
 * ordinary — and because the answer is going straight to a screen, where an exception is a
 * failure and a refusal is information.
 */
public interface ConnectorCredentialProbe {

    /** The source system this probes, matching a {@link ConnectorSourceProfile#sourceSystem()}. */
    String sourceSystem();

    ConnectorCredentialProbeResult probe(SecretValue credential);
}
