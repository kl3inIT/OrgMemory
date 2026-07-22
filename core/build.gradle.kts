plugins {
    id("orgmemory.spring-library-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.modulith:spring-modulith-starter-core")
    api("org.springframework.ai:spring-ai-client-chat")

    implementation("org.springframework.modulith:spring-modulith-starter-jpa")

    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
