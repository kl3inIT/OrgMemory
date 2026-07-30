plugins {
    id("orgmemory.spring-library-conventions")
}

dependencies {
    api(project(":components:graph-rag-core"))
    implementation("io.micrometer:context-propagation")
    implementation("io.micrometer:micrometer-core")
    implementation("io.opentelemetry:opentelemetry-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation("io.opentelemetry:opentelemetry-sdk")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    // Drives the real bridge path the sanitizer sits in rather than a copy of it.
    testImplementation("io.micrometer:micrometer-tracing-bridge-otel")
    // The allowlist gate asserts a GraphRAG span through the sanitizer in the position the
    // sanitizer occupies in production, so it needs the boundary module the applications get
    // separately. This is the one place the two modules meet, and it is a test.
    testImplementation(project(":integrations:observability"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
