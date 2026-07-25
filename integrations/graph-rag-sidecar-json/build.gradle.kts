plugins {
    id("orgmemory.java-library-conventions")
}

dependencies {
    api(project(":components:graph-rag-core"))
    implementation(platform(libs.spring.boot.dependencies))
    implementation("tools.jackson.core:jackson-databind")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
