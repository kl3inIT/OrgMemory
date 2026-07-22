plugins {
    id("orgmemory.spring-boot-app-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":integrations:authorization-openfga"))
    implementation(project(":integrations:object-storage-minio"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(libs.apache.poi.ooxml)
    implementation(libs.spring.ai.starter.openai)
    implementation("org.springframework.ai:spring-ai-tika-document-reader")
    implementation("org.springframework.ai:spring-ai-pdf-document-reader")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.register<JavaExec>("validatePermissionDataset") {
    group = "verification"
    description = "Validates an external permission benchmark workbook."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.orgmemory.worker.dataset.PermissionDatasetValidationCli")
}
