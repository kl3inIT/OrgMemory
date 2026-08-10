plugins {
    id("orgmemory.spring-library-conventions")
}

dependencies {
    api(project(":components:graph-rag-core"))
    implementation("org.springframework.ai:spring-ai-tika-document-reader")
    implementation("org.springframework.ai:spring-ai-pdf-document-reader")
    implementation("org.jsoup:jsoup:1.22.2")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.apache.poi.ooxml)
    testImplementation(libs.apache.poi.scratchpad)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
