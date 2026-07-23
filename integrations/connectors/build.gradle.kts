plugins {
    id("orgmemory.spring-library-conventions")
}

// One module for every source adapter, a package per source. Source SDKs and wire
// shapes stay out of core and out of the apps; adding a source adds a package, not
// a Gradle module. Splitting later is mechanical because each source already sits
// behind the same ConnectorBatchSource port.
dependencies {
    implementation(project(":core"))
    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot")
    implementation("tools.jackson.core:jackson-databind")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.hamcrest:hamcrest")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
