plugins {
    id("orgmemory.spring-library-conventions")
}

dependencies {
    api(project(":components:graph-rag-core"))
    implementation("org.springframework:spring-jdbc")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-jdbc")
    implementation("org.springframework.boot:spring-boot-sql")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(project(":core"))
    testImplementation(project(":components:graph-rag-testkit"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
