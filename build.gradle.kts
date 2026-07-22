import org.gradle.api.GradleException
import org.gradle.api.tasks.Exec

// Root build. Shared configuration lives in build-logic convention plugins.

group = "com.orgmemory"
version = "0.0.1-SNAPSHOT"

fun Exec.demoPowerShell(scriptName: String) {
    val windows = System.getProperty("os.name").lowercase().contains("windows")
    if (!windows) {
        doFirst {
            throw GradleException(
                "The repository-local demo launcher currently requires PowerShell on Windows. " +
                    "Run the equivalent scripts manually on another platform."
            )
        }
    }
    commandLine(
        "powershell.exe",
        "-NoLogo",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        layout.projectDirectory.file("scripts/$scriptName").asFile.absolutePath
    )
}

tasks.register<Exec>("demoBootstrap") {
    group = "demo"
    description = "Starts local infrastructure and imports Keycloak, OpenFGA, and available PostgreSQL demo fixtures."
    demoPowerShell("bootstrap-demo.ps1")
}

tasks.register<Exec>("demoSeed") {
    group = "demo"
    description = "Imports the idempotent PostgreSQL demo directory after Flyway has created the schema."
    demoPowerShell("seed-demo-directory.ps1")
}
