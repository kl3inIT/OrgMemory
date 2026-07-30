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
include(":components:scim-protocol-conformance")
include(":integrations:authorization-openfga")
include(":integrations:connectors")
include(":integrations:ai-model-gateways")
include(":integrations:graph-rag-postgres")
include(":integrations:graph-rag-opensearch")
include(":integrations:graph-rag-neo4j")
include(":integrations:graph-rag-observability")
include(":integrations:observability")
include(":integrations:graph-rag-sidecar-json")
include(":integrations:graph-rag-spring-ai")
include(":integrations:object-storage-minio")
