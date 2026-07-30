plugins {
    id("orgmemory.spring-boot-app-conventions")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Without this the gateway starts no trace of its own, so a call into the API
    // arrives with no context to continue and the two halves cannot be joined.
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    // Supplies RestClientBuilderConfigurer and the customizers that write
    // traceparent; the MCP webmvc starter brings the server half only.
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation(libs.spring.ai.starter.mcp.server.webmvc)
    implementation(libs.bucket4j.core)
    implementation(libs.caffeine)

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
