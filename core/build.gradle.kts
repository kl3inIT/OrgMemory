plugins {
    id("orgmemory.spring-library-conventions")
}

dependencies {
    api(project(":components:graph-rag-core"))
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.modulith:spring-modulith-starter-core")
    api("org.springframework.ai:spring-ai-client-chat")

    implementation("org.springframework.modulith:spring-modulith-starter-jpa")
    // The assistant turn observation and the meter handler that reads it. Both arrive
    // transitively through the Spring Boot starters; declared because code here compiles
    // against them, and a transitive dependency is not a promise.
    implementation("io.micrometer:micrometer-observation")
    implementation("io.micrometer:micrometer-core")
    // Vetted AES-GCM rather than hand-rolled: the standalone crypto module, not the web stack.
    implementation("org.springframework.security:spring-security-crypto")
    implementation(libs.commons.compress)
    implementation(libs.snakeyaml)

    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
