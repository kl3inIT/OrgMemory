package com.orgmemory.graphrag.opensearch;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orgmemory.graph-rag.opensearch")
public class OpenSearchGraphRagProperties {

    private boolean enabled;
    private URI endpoint = URI.create("http://localhost:9200");
    private String username = "";
    private String password = "";
    private String indexPrefix = "orgmemory-graphrag";
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration socketTimeout = Duration.ofSeconds(30);
    private int bulkMaximumOperations = 500;
    private int graphMaximumFrontier = 1_000;
    private boolean pplGraphLookupEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(URI endpoint) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = normalizeOptional(username);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = Objects.requireNonNull(password, "password");
    }

    public String getIndexPrefix() {
        return indexPrefix;
    }

    public void setIndexPrefix(String indexPrefix) {
        this.indexPrefix = requireIndexPrefix(indexPrefix);
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = requirePositive(connectTimeout, "connectTimeout");
    }

    public Duration getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(Duration socketTimeout) {
        this.socketTimeout = requirePositive(socketTimeout, "socketTimeout");
    }

    public int getBulkMaximumOperations() {
        return bulkMaximumOperations;
    }

    public void setBulkMaximumOperations(int bulkMaximumOperations) {
        if (bulkMaximumOperations <= 0) {
            throw new IllegalArgumentException("bulkMaximumOperations must be positive");
        }
        this.bulkMaximumOperations = bulkMaximumOperations;
    }

    public int getGraphMaximumFrontier() {
        return graphMaximumFrontier;
    }

    public void setGraphMaximumFrontier(int graphMaximumFrontier) {
        if (graphMaximumFrontier <= 0) {
            throw new IllegalArgumentException("graphMaximumFrontier must be positive");
        }
        this.graphMaximumFrontier = graphMaximumFrontier;
    }

    public boolean isPplGraphLookupEnabled() {
        return pplGraphLookupEnabled;
    }

    public void setPplGraphLookupEnabled(boolean pplGraphLookupEnabled) {
        this.pplGraphLookupEnabled = pplGraphLookupEnabled;
    }

    void validate() {
        String scheme = endpoint.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException(
                    "endpoint scheme must be http or https");
        }
        if (endpoint.getHost() == null || endpoint.getHost().isBlank()) {
            throw new IllegalArgumentException("endpoint must include a host");
        }
        if (endpoint.getQuery() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException(
                    "endpoint must not include a query or fragment");
        }
        if (username.isBlank() != password.isBlank()) {
            throw new IllegalArgumentException(
                    "username and password must be configured together");
        }
    }

    private static String requireIndexPrefix(String value) {
        String normalized = Objects.requireNonNull(value, "indexPrefix")
                .strip()
                .toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9_-]*")) {
            throw new IllegalArgumentException(
                    "indexPrefix must contain only lowercase letters, digits, '_' or '-'");
        }
        if (normalized.length() > 180) {
            throw new IllegalArgumentException(
                    "indexPrefix must not exceed 180 ASCII characters");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return Objects.requireNonNull(value, "value").strip();
    }

    private static Duration requirePositive(Duration value, String field) {
        Duration duration = Objects.requireNonNull(value, field);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return duration;
    }
}
