package com.orgmemory.integrations.observability;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Runs {@link ObservationContentBoundaryVerifier} during context refresh, so an application
 * configured to capture prompt or completion content fails to start instead of serving traffic
 * while writing it.
 *
 * <p>Kept apart from {@link ProviderLoggingBoundaryAutoConfiguration} because the two answer
 * different questions and fail with different instructions: one reads a resolved logger level,
 * the other a resolved property. A single verifier would have to explain both in one message.
 *
 * <p>There is no property to disable this: a boundary with an off switch is a default, not a
 * boundary.
 */
@AutoConfiguration
public class ObservationContentBoundaryAutoConfiguration {

    @Bean
    InitializingBean observationContentBoundaryVerification(Environment environment) {
        return () -> ObservationContentBoundaryVerifier.verify(environment);
    }
}
