plugins {
    id("orgmemory.java-library-conventions")
}

dependencies {
    api(project(":components:graph-rag-core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
