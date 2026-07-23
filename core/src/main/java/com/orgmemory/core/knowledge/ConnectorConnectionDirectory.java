package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.secret.SecretValue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tells a connector which connections to crawl and what to authenticate with.
 *
 * <p>This is what makes a connection administrable rather than deployed. An adapter that read
 * its workspace and token from configuration could only be changed by editing the worker's
 * environment and restarting it, which puts every connection change in the hands of whoever
 * can reach the host; asking the ledger each poll means an administrator's decision takes
 * effect on the next one.
 *
 * <p>Narrow on purpose. A crawl needs to know which connections are enabled and how to
 * authenticate to them, and nothing else about the administration surface belongs on the path
 * a background worker takes.
 */
public interface ConnectorConnectionDirectory {

    /**
     * Every connection of one source system that an administrator has enabled, across tenants.
     * A worker serves all of them, so this deliberately is not scoped to an organization.
     */
    List<ConnectorCrawlConfiguration> enabledCrawls(String sourceSystem);

    /**
     * The credential for one connection, or empty when none is stored.
     *
     * <p>Empty is an ordinary answer rather than a failure: a connection can be configured and
     * enabled before anybody has pasted a token into it, and that is a connection that produces
     * nothing yet rather than one that is broken.
     */
    Optional<SecretValue> resolveCredential(
            UUID organizationId, String sourceSystem, String sourceConnectionKey);
}
