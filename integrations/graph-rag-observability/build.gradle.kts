plugins {
    id("orgmemory.spring-library-conventions")
}

dependencies {
    api(project(":components:graph-rag-core"))
    implementation("io.micrometer:micrometer-core")
    implementation("io.opentelemetry:opentelemetry-api")
    implementation("io.opentelemetry:opentelemetry-sdk-trace")
    implementation("org.slf4j:slf4j-api")
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
    // The logging boundary is only meaningful against the real leak sites and a real logging
    // backend, so the verifier is tested against both rather than against stand-ins.
    testImplementation("org.springframework.ai:spring-ai-openai")
    testImplementation("org.springframework.ai:spring-ai-anthropic")
    testImplementation("ch.qos.logback:logback-classic")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
