import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("orgmemory.spring-boot-app-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":integrations:ai-model-gateways"))
    implementation(project(":integrations:authorization-openfga"))
    implementation(project(":integrations:connectors"))
    implementation(project(":integrations:graph-rag-postgres"))
    implementation(project(":integrations:graph-rag-observability"))
    implementation(project(":integrations:graph-rag-spring-ai"))
    implementation(project(":integrations:object-storage-minio"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation(libs.springdoc.webmvc)
    implementation(libs.spring.ai.starter.openai)
    implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
    runtimeOnly("org.springframework.modulith:spring-modulith-runtime")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // Both arrive at runtime through the OpenTelemetry starter; the context test names
    // their types to assert which telemetry beans the running application does and does
    // not have.
    testImplementation("io.micrometer:micrometer-registry-otlp")
    testImplementation("io.opentelemetry:opentelemetry-sdk")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    systemProperty("spring.session.jdbc.cleanup-cron", "-")
    systemProperty("orgmemory.graph-rag.postgres.topology-backend", "RELATIONAL")
}

tasks.named<ProcessResources>("processTestResources") {
    from(rootProject.file("demo/fixtures/asset-registry")) {
        into("golden/asset-registry")
    }
}
