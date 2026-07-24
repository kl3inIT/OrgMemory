package com.orgmemory.graphrag.neo4j;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orgmemory.graph-rag.neo4j")
public class Neo4jGraphRagProperties {

    private boolean enabled;
    private URI uri = URI.create("neo4j://localhost:7687");
    private String username = "neo4j";
    private String password = "";
    private String database = "neo4j";
    private int maximumConnectionPoolSize = 50;
    private Duration connectionTimeout = Duration.ofSeconds(10);
    private Duration connectionAcquisitionTimeout = Duration.ofSeconds(30);
    private Duration maximumTransactionRetryTime = Duration.ofSeconds(15);
    private Duration queryTimeout = Duration.ofSeconds(30);
    private long fetchSize = 1_000;
    private int writeBatchSize = 500;
    private int copyPageSize = 1_000;
    private int maximumFrontier = 1_000;
    private Duration copyWaitTimeout = Duration.ofMinutes(2);
    private Duration copyLease = Duration.ofMinutes(1);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = Objects.requireNonNull(uri, "uri");
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = requireText(username, "username");
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = Objects.requireNonNull(password, "password");
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = requireIdentifier(database, "database");
    }

    public int getMaximumConnectionPoolSize() {
        return maximumConnectionPoolSize;
    }

    public void setMaximumConnectionPoolSize(int maximumConnectionPoolSize) {
        this.maximumConnectionPoolSize =
                requirePositive(maximumConnectionPoolSize, "maximumConnectionPoolSize");
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = requirePositive(connectionTimeout, "connectionTimeout");
    }

    public Duration getConnectionAcquisitionTimeout() {
        return connectionAcquisitionTimeout;
    }

    public void setConnectionAcquisitionTimeout(Duration connectionAcquisitionTimeout) {
        this.connectionAcquisitionTimeout =
                requirePositive(connectionAcquisitionTimeout, "connectionAcquisitionTimeout");
    }

    public Duration getMaximumTransactionRetryTime() {
        return maximumTransactionRetryTime;
    }

    public void setMaximumTransactionRetryTime(Duration maximumTransactionRetryTime) {
        this.maximumTransactionRetryTime =
                requirePositive(maximumTransactionRetryTime, "maximumTransactionRetryTime");
    }

    public Duration getQueryTimeout() {
        return queryTimeout;
    }

    public void setQueryTimeout(Duration queryTimeout) {
        this.queryTimeout = requirePositive(queryTimeout, "queryTimeout");
    }

    public long getFetchSize() {
        return fetchSize;
    }

    public void setFetchSize(long fetchSize) {
        if (fetchSize <= 0) {
            throw new IllegalArgumentException("fetchSize must be positive");
        }
        this.fetchSize = fetchSize;
    }

    public int getWriteBatchSize() {
        return writeBatchSize;
    }

    public void setWriteBatchSize(int writeBatchSize) {
        this.writeBatchSize = requirePositive(writeBatchSize, "writeBatchSize");
    }

    public int getCopyPageSize() {
        return copyPageSize;
    }

    public void setCopyPageSize(int copyPageSize) {
        this.copyPageSize = requirePositive(copyPageSize, "copyPageSize");
    }

    public int getMaximumFrontier() {
        return maximumFrontier;
    }

    public void setMaximumFrontier(int maximumFrontier) {
        this.maximumFrontier = requirePositive(maximumFrontier, "maximumFrontier");
    }

    public Duration getCopyWaitTimeout() {
        return copyWaitTimeout;
    }

    public void setCopyWaitTimeout(Duration copyWaitTimeout) {
        this.copyWaitTimeout = requirePositive(copyWaitTimeout, "copyWaitTimeout");
    }

    public Duration getCopyLease() {
        return copyLease;
    }

    public void setCopyLease(Duration copyLease) {
        this.copyLease = requirePositive(copyLease, "copyLease");
    }

    void validate() {
        String scheme = uri.getScheme();
        if (scheme == null
                || !java.util.Set.of(
                                "neo4j",
                                "neo4j+s",
                                "neo4j+ssc",
                                "bolt",
                                "bolt+s",
                                "bolt+ssc")
                        .contains(scheme)) {
            throw new IllegalArgumentException("unsupported Neo4j URI scheme");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Neo4j URI must include a host");
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Neo4j URI must not include a query or fragment");
        }
        if (uri.getUserInfo() != null
                || (uri.getPath() != null
                        && !uri.getPath().isBlank()
                        && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException(
                    "Neo4j URI must not embed credentials or a database path");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException(
                    "Neo4j password must be configured when the adapter is enabled");
        }
        if (copyLease.compareTo(queryTimeout) <= 0) {
            throw new IllegalArgumentException(
                    "copyLease must be longer than queryTimeout");
        }
        if (copyWaitTimeout.compareTo(copyLease) <= 0) {
            throw new IllegalArgumentException(
                    "copyWaitTimeout must be longer than copyLease");
        }
    }

    private static int requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String field) {
        Duration duration = Objects.requireNonNull(value, field);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return duration;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String requireIdentifier(String value, String field) {
        String normalized = requireText(value, field);
        if (!normalized.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException(
                    field + " contains unsupported characters");
        }
        return normalized;
    }
}
