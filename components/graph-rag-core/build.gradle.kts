plugins {
    id("orgmemory.java-library-conventions")
}

dependencies {
    testImplementation(project(":components:graph-rag-testkit"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
