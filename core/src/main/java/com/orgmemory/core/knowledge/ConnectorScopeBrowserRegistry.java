package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.secret.SecretValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The scope browsers adapters contributed, keyed by source system. Separate from the profile and
 * the probe registries for the same reason those are separate from each other: a source can be
 * ingestible, or checkable, without having anything to enumerate.
 */
@Service
public class ConnectorScopeBrowserRegistry {

    private final Map<String, ConnectorScopeBrowser> browsers;

    ConnectorScopeBrowserRegistry(List<ConnectorScopeBrowser> contributed) {
        Map<String, ConnectorScopeBrowser> collected = new LinkedHashMap<>();
        for (ConnectorScopeBrowser browser : contributed) {
            ConnectorScopeBrowser clash =
                    collected.putIfAbsent(normalize(browser.sourceSystem()), browser);
            if (clash != null) {
                throw new IllegalStateException(
                        "Two adapters both enumerate the source system " + browser.sourceSystem());
            }
        }
        this.browsers = Map.copyOf(collected);
    }

    /**
     * @throws UnsupportedConnectorSourceException when the source contributed no browser, which is
     *         a different fact from there being nothing to crawl and is reported as one
     */
    public List<ConnectorScope> scopes(String sourceSystem, SecretValue credential, String sourceConfig) {
        ConnectorScopeBrowser browser = browsers.get(normalize(sourceSystem));
        if (browser == null) {
            throw new UnsupportedConnectorSourceException(
                    "Listing what can be crawled is not supported for the source system " + sourceSystem);
        }
        return browser.scopes(credential, sourceConfig);
    }

    public boolean supports(String sourceSystem) {
        return browsers.containsKey(normalize(sourceSystem));
    }

    private static String normalize(String sourceSystem) {
        return sourceSystem == null ? "" : sourceSystem.trim().toLowerCase();
    }
}
