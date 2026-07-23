plugins {
    id("orgmemory.spring-library-conventions")
}

dependencies {
    api(project(":components:graph-rag-core"))
    api(libs.spring.ai.client.chat)

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
