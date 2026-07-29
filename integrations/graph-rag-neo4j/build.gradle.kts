plugins {
    id("orgmemory.spring-library-conventions")
}

dependencies {
    api(project(":components:graph-rag-core"))
    implementation(libs.neo4j.java.driver)
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(project(":components:graph-rag-testkit"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.testcontainers:testcontainers-neo4j")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
