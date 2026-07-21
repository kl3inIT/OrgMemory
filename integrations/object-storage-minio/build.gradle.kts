plugins {
    id("orgmemory.spring-library-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.minio.sdk)
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
