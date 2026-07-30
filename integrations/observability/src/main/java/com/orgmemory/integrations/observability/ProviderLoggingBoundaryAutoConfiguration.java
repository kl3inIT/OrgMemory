package com.orgmemory.integrations.observability;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Runs {@link ProviderLoggingBoundaryVerifier} during context refresh, so an application
 * whose logging configuration would leak prompts fails to start instead of serving traffic.
 *
 * <p>Logging levels are applied long before refresh, and the actuator loggers endpoint is not
 * exposed, so the level observed here is the one the provider libraries will see. There is no
 * property to disable this: a boundary with an off switch is a default, not a boundary.
 */
@AutoConfiguration
public class ProviderLoggingBoundaryAutoConfiguration {

    @Bean
    InitializingBean providerLoggingBoundaryVerification() {
        return () -> ProviderLoggingBoundaryVerifier.verify(getClass().getClassLoader());
    }
}
