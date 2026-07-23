package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.secret.SecretValue;
import java.util.List;

/**
 * Lists what a connection could be pointed at, so choosing it is picking from what exists rather
 * than typing an id read off somewhere else.
 *
 * <p>Optional, like {@link ConnectorCredentialProbe}. A source whose scope is the whole account
 * has nothing to enumerate, and an adapter should not have to invent a list to satisfy a port.
 *
 * <p>Enumerating is deliberately separate from gaining access. This answers what is there and
 * whether the credential can read it; a crawl acts on that answer. An administrator choosing a
 * scope is the instruction, and the adapter carries it out when it next crawls — which also
 * repairs the case where access was withdrawn at the source afterwards.
 */
public interface ConnectorScopeBrowser {

    String sourceSystem();

    /**
     * @param credential   the connection's stored credential
     * @param sourceConfig the connection's own configuration document, for an adapter whose
     *                     enumeration depends on it
     */
    List<ConnectorScope> scopes(SecretValue credential, String sourceConfig);
}
