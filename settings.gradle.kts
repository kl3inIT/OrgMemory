rootProject.name = "orgmemory"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

includeBuild("build-logic")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":core")
include(":apps:api")
include(":apps:mcp")
include(":apps:worker")
include(":components:graph-rag-core")
include(":components:graph-rag-testkit")
include(":integrations:authorization-openfga")
include(":integrations:ai-openai-compatible")
include(":integrations:graph-rag-spring-ai")
include(":integrations:object-storage-minio")
