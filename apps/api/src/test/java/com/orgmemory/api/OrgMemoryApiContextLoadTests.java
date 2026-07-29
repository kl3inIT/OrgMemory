package com.orgmemory.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "management.endpoint.health.probes.enabled=true")
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrgMemoryApiContextLoadTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext context;

    @Test
    void contextLoads() {
        // Success means Flyway ran and Hibernate ddl-auto=validate accepted the schema.
    }

    @Test
    void noMetricsExporterStartsWithoutACollectorToExportTo() {
        // Micrometer's OTLP registry is opt-out and defaults its URL to localhost:4318,
        // which inside a container is that container. Production ran this way for weeks,
        // failing and logging every minute. An application with no collector configured
        // must create no exporter at all.
        //
        // No @AutoConfigureMetrics is needed here, and adding it would not make the
        // assertion stronger. The context customizer that disables metrics export in tests
        // ships in spring-boot-micrometer-metrics-test, which this module does not depend
        // on, so OtlpMetricsExportAutoConfiguration really does run. Verified by flipping
        // management.otlp.metrics.export.enabled to true, which fails this assertion.
        assertTrue(
                context.getBeansOfType(OtlpMeterRegistry.class).isEmpty(),
                "an OTLP meter registry exists with no collector configured, so it is "
                        + "pushing to nowhere on a timer");
    }

    @Test
    void telemetryCarriesEnoughIdentityForACollectorToTellDeploymentsApart() {
        Attributes attributes = context.getBean(Resource.class).getAttributes();

        assertNotNull(attributes.get(AttributeKey.stringKey("service.version")));
        assertNotNull(attributes.get(AttributeKey.stringKey("deployment.environment.name")));
    }

    @Test
    void healthProbesRemainPublicWhileOtherActuatorEndpointsStayProtected() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }
}
