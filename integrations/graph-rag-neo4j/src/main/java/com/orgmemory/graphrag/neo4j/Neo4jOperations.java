package com.orgmemory.graphrag.neo4j;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.exceptions.Neo4jException;

final class Neo4jOperations {

    private final Driver driver;
    private final SessionConfig sessionConfig;
    private final TransactionConfig transactionConfig;

    Neo4jOperations(Driver driver, String database, Duration queryTimeout) {
        this.driver = Objects.requireNonNull(driver, "driver");
        this.sessionConfig = SessionConfig.builder()
                .withDatabase(Objects.requireNonNull(database, "database"))
                .build();
        this.transactionConfig = TransactionConfig.builder()
                .withTimeout(Objects.requireNonNull(queryTimeout, "queryTimeout"))
                .build();
    }

    <T> T read(Function<TransactionContext, T> work) {
        Objects.requireNonNull(work, "work");
        try (Session session = driver.session(sessionConfig)) {
            return session.executeRead(work::apply, transactionConfig);
        } catch (Neo4jException exception) {
            throw new Neo4jGraphProjectionException(
                    "Neo4j read transaction failed [" + exception.code() + "]",
                    exception);
        }
    }

    <T> T write(Function<TransactionContext, T> work) {
        Objects.requireNonNull(work, "work");
        try (Session session = driver.session(sessionConfig)) {
            return session.executeWrite(work::apply, transactionConfig);
        } catch (Neo4jException exception) {
            throw new Neo4jGraphProjectionException(
                    "Neo4j write transaction failed [" + exception.code() + "]",
                    exception);
        }
    }

    void writeWithoutResult(java.util.function.Consumer<TransactionContext> work) {
        Objects.requireNonNull(work, "work");
        write(transaction -> {
            work.accept(transaction);
            return null;
        });
    }
}
