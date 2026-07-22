plugins {
    id("orgmemory.spring-library-conventions")
}

dependencies {
    api(project(":core"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.ai:spring-ai-openai")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
