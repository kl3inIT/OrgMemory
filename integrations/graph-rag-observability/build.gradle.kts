plugins {
    id("orgmemory.spring-library-conventions")
}

dependencies {
    api(project(":components:graph-rag-core"))
    implementation("io.opentelemetry:opentelemetry-api")
    implementation("io.opentelemetry:opentelemetry-sdk-trace")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    // Supplies the SpanExporters collection this module replaces with a sanitized copy.
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")

    testImplementation("io.opentelemetry:opentelemetry-sdk")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    // Drives the real bridge path this module sanitizes rather than a copy of it.
    testImplementation("io.micrometer:micrometer-tracing-bridge-otel")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
