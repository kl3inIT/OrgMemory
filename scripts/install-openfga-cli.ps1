param(
    [string]$Version = "0.7.19"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$destination = Join-Path $repoRoot ".tools\openfga"
$archiveName = "fga_${Version}_windows_amd64.tar.gz"
$archive = Join-Path $destination $archiveName
$checksums = Join-Path $destination "checksums.txt"
$cli = Join-Path $destination "fga.exe"
$releaseBaseUrl = "https://github.com/openfga/cli/releases/download/v$Version"

New-Item -ItemType Directory -Force -Path $destination | Out-Null

if (Test-Path -LiteralPath $cli) {
    $installedVersion = (& $cli version 2>$null) -join " "
    if ($LASTEXITCODE -eq 0 -and $installedVersion -match [regex]::Escape($Version)) {
        Write-Host "OpenFGA CLI v$Version is already installed at $cli"
        exit 0
    }
}

Invoke-WebRequest -Uri "$releaseBaseUrl/checksums.txt" -OutFile $checksums
Invoke-WebRequest -Uri "$releaseBaseUrl/$archiveName" -OutFile $archive

$checksumLine = Select-String -LiteralPath $checksums -Pattern "\s$([regex]::Escape($archiveName))$" |
    Select-Object -First 1
if ($null -eq $checksumLine) {
    throw "The official checksum file does not contain $archiveName."
}

$expectedHash = ($checksumLine.Line -split "\s+")[0].ToUpperInvariant()
$actualHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
if ($actualHash -ne $expectedHash) {
    throw "OpenFGA CLI checksum mismatch. Expected $expectedHash but got $actualHash."
}

& tar.exe -xzf $archive -C $destination
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $cli)) {
    throw "OpenFGA CLI extraction failed."
}

& $cli version
if ($LASTEXITCODE -ne 0) {
    throw "The installed OpenFGA CLI could not start."
}

Write-Host "Installed the official OpenFGA CLI v$Version at $cli"
