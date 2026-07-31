plugins {
    id("orgmemory.java-library-conventions")
}

val pingCandidate by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    pingCandidate("com.unboundid.product.scim2:scim2-sdk-common:6.0.0")

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation("com.unboundid.product.scim2:scim2-sdk-common:6.0.0")
    testImplementation("org.apache.directory.scimple:scim-spec-schema:1.0.0-M1")
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.register("verifyPingCandidateSurface") {
    group = "verification"
    description = "Rejects HTTP runtimes and Jackson 2 from the selected SCIM parser dependency graph."
    inputs.files(pingCandidate)

    doLast {
        val modules = inputs.files.files
            .map { it.name }
            .sorted()
        val forbidden = modules.filter {
            (it.startsWith("jackson-") &&
                !it.startsWith("jackson-annotations-") &&
                Regex("-2\\.[0-9]").containsMatchIn(it)) ||
                it.startsWith("javax.ws.rs-api-") ||
                it.startsWith("jakarta.ws.rs-api-") ||
                it.startsWith("jersey-") ||
                it.startsWith("resteasy-")
        }

        check(forbidden.isEmpty()) {
            "Selected SCIM dependency leaks a forbidden runtime: ${forbidden.joinToString()}"
        }
    }
}

tasks.test {
    dependsOn("verifyPingCandidateSurface")
}
