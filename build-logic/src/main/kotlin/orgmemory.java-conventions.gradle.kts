import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

plugins {
    java
}

abstract class TestcontainersLock : BuildService<BuildServiceParameters.None>

val testcontainersLock = gradle.sharedServices.registerIfAbsent("testcontainersLock", TestcontainersLock::class) {
    maxParallelUsages = 1
}

group = "com.orgmemory"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

tasks.withType<Test> {
    useJUnitPlatform()
    usesService(testcontainersLock)
}
