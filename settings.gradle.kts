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
include(":integrations:authorization-openfga")
include(":integrations:object-storage-minio")
