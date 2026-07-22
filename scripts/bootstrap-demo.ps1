param(
    [switch]$SkipDirectorySeed
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($env:ORGMEMORY_OBJECT_STORAGE_ACCESS_KEY)) {
    $env:ORGMEMORY_OBJECT_STORAGE_ACCESS_KEY = "orgmemory-local"
}
if ([string]::IsNullOrWhiteSpace($env:ORGMEMORY_OBJECT_STORAGE_SECRET_KEY)) {
    $env:ORGMEMORY_OBJECT_STORAGE_SECRET_KEY = "orgmemory-local-secret"
}

& docker compose --project-directory $repoRoot up -d --wait
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose could not start the local infrastructure."
}

# Keycloak startup import skips an existing realm. Recreate only the ephemeral
# local Keycloak container so the committed realm fixture is applied every time.
& docker compose --project-directory $repoRoot up -d --wait --force-recreate keycloak
if ($LASTEXITCODE -ne 0) {
    throw "Keycloak demo realm import failed."
}

& (Join-Path $PSScriptRoot "bootstrap-openfga.ps1")
if ($LASTEXITCODE -ne 0) {
    throw "OpenFGA demo bootstrap failed."
}

if (-not $SkipDirectorySeed) {
    $schemaReady = (& docker compose --project-directory $repoRoot exec -T postgres `
        psql -U orgmemory -d orgmemory -tAc "SELECT to_regclass('public.app_users') IS NOT NULL") -join ""
    if ($LASTEXITCODE -eq 0 -and $schemaReady.Trim() -eq "t") {
        & (Join-Path $PSScriptRoot "seed-demo-directory.ps1")
        if ($LASTEXITCODE -ne 0) {
            throw "PostgreSQL demo directory import failed."
        }
    } else {
        Write-Warning "Flyway has not created the application schema yet. Start apps/api once, then run '.\gradlew.bat demoSeed'."
    }
}

Write-Host "Demo infrastructure and identity/authorization fixtures are ready."
