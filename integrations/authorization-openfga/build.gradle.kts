plugins {
    id("orgmemory.spring-library-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.openfga.sdk)

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
