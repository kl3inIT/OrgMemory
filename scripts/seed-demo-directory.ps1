param()

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$fixture = Join-Path $repoRoot "demo\fixtures\postgres\directory.sql"

if ([string]::IsNullOrWhiteSpace($env:ORGMEMORY_OBJECT_STORAGE_ACCESS_KEY)) {
    $env:ORGMEMORY_OBJECT_STORAGE_ACCESS_KEY = "orgmemory-local"
}
if ([string]::IsNullOrWhiteSpace($env:ORGMEMORY_OBJECT_STORAGE_SECRET_KEY)) {
    $env:ORGMEMORY_OBJECT_STORAGE_SECRET_KEY = "orgmemory-local-secret"
}

if (-not (Test-Path -LiteralPath $fixture)) {
    throw "PostgreSQL demo fixture does not exist: $fixture"
}

$schemaReady = (& docker compose --project-directory $repoRoot exec -T postgres `
    psql -U orgmemory -d orgmemory -tAc "SELECT to_regclass('public.app_users') IS NOT NULL") -join ""
if ($LASTEXITCODE -ne 0) {
    throw "Could not inspect the OrgMemory PostgreSQL schema."
}
if ($schemaReady.Trim() -ne "t") {
    throw "The OrgMemory schema is not ready. Start apps/api once so Flyway can migrate the database, then run demoSeed again."
}

# PowerShell encodes anything piped to a native executable using $OutputEncoding, whose
# default drops non-ASCII. That silently turned the Vietnamese names in the fixture into
# question marks inside PostgreSQL, so the encoding is pinned for the duration of the pipe.
$previousOutputEncoding = $OutputEncoding
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
try {
    Get-Content -LiteralPath $fixture -Raw -Encoding UTF8 |
        docker compose --project-directory $repoRoot exec -T postgres `
            psql -v ON_ERROR_STOP=1 -U orgmemory -d orgmemory
}
finally {
    $OutputEncoding = $previousOutputEncoding
}
if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL demo directory import failed."
}

$counts = (& docker compose --project-directory $repoRoot exec -T postgres `
    psql -U orgmemory -d orgmemory -tAc `
        "SELECT count(*) || ' users, ' || (SELECT count(*) FROM departments WHERE id::text LIKE 'd2000000-%') || ' departments' FROM app_users WHERE email LIKE '%@synthetic.local'") -join ""

Write-Host "PostgreSQL demo directory imported: $($counts.Trim())"
