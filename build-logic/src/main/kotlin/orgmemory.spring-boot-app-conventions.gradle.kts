plugins {
    id("orgmemory.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.1.0")
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.0")
    }
}

dependencies {
    // The payload boundary arrives with the convention rather than by each application
    // remembering to ask for it. `apps/mcp` exported spans with no sanitizer and no verifier
    // for exactly as long as taking them meant taking GraphRAG as well; a boundary an
    // application can forget is a default. Taking this plugin is taking the boundary.
    add("implementation", project(":integrations:observability"))
}
