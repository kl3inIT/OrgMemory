plugins {
    id("orgmemory.java-library-conventions")
}

sourceSets {
    test {
        resources.srcDir(rootProject.layout.projectDirectory.dir("evaluation/baselines"))
    }
}

dependencies {
    testImplementation(project(":components:graph-rag-testkit"))
    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation("tools.jackson.core:jackson-databind")
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
