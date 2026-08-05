#Requires -PSEdition Core

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z0-9._@-]+$')]
    [string]$SshTarget,

    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_]{0,62}$')]
    [string]$RestoredDatabase,

    [string]$Output = 'evaluation/output/retrieval-observations-v2.json'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$tunnel = $null

function Invoke-SshText {
    param([Parameter(Mandatory)][string]$RemoteCommand)
    $result = & ssh.exe -o BatchMode=yes -o ConnectTimeout=10 $SshTarget $RemoteCommand
    if ($LASTEXITCODE -ne 0) {
        throw "SSH command failed with exit code $LASTEXITCODE."
    }
    return ($result -join "`n")
}

function Assert-LocalPortFree {
    param([int]$Port)
    $listener = [System.Net.Sockets.TcpListener]::new(
        [System.Net.IPAddress]::Loopback,
        $Port
    )
    try { $listener.Start() } finally { $listener.Stop() }
}

function Start-CaptureProcess {
    param([Parameter(Mandatory)][hashtable]$Environment)
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'cmd.exe'
    $startInfo.WorkingDirectory = $repoRoot
    $startInfo.UseShellExecute = $false
    $startInfo.Arguments = '/d /s /c ""{0}\gradlew.bat" --no-daemon :apps:api:bootRun"' -f $repoRoot
    foreach ($entry in $Environment.GetEnumerator()) {
        $startInfo.Environment[$entry.Key] = [string]$entry.Value
    }
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw 'Could not start the retrieval observation process.'
    }
    $process.WaitForExit()
    $exitCode = $process.ExitCode
    $process.Dispose()
    if ($exitCode -ne 0) {
        throw "Retrieval observation capture failed with exit code $exitCode."
    }
}

if ($RestoredDatabase -ieq 'orgmemory') {
    throw 'The live orgmemory database cannot be used for retrieval observations.'
}

Push-Location $repoRoot
try {
    foreach ($port in @(15432, 18081, 19000)) { Assert-LocalPortFree $port }
    $json = Invoke-SshText(
        'cd /apps/orgmemory && ./infrastructure/deployment/scripts/export-team-dev-config.sh'
    )
    $config = $json | ConvertFrom-Json -AsHashtable
    $tunnelArguments = @(
        '-N', '-o', 'BatchMode=yes', '-o', 'ExitOnForwardFailure=yes',
        '-o', 'ServerAliveInterval=30', '-o', 'ServerAliveCountMax=3',
        '-L', "127.0.0.1:15432:$($config.postgresTarget)",
        '-L', "127.0.0.1:18081:$($config.openfgaTarget)",
        '-L', "127.0.0.1:19000:$($config.minioTarget)",
        $SshTarget
    )
    $tunnel = Start-Process 'ssh.exe' $tunnelArguments -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 1
    if ($tunnel.HasExited) {
        throw 'SSH tunnel exited before the capture started.'
    }

    $environment = @{}
    foreach ($entry in $config.GetEnumerator()) {
        if ($entry.Key -like 'ORGMEMORY_*' `
                -and -not [string]::IsNullOrEmpty([string]$entry.Value)) {
            $environment[$entry.Key] = $entry.Value
        }
    }
    $environment['ORGMEMORY_DB_URL'] =
        "jdbc:postgresql://127.0.0.1:15432/$RestoredDatabase"
    $environment['ORGMEMORY_OPENFGA_API_URL'] = 'http://127.0.0.1:18081'
    $environment['ORGMEMORY_AUTHORIZATION_OPENFGA_API_URL'] = 'http://127.0.0.1:18081'
    $environment['ORGMEMORY_AUTHORIZATION_OPENFGA_STORE_ID'] =
        $config.ORGMEMORY_OPENFGA_STORE_ID
    $environment['ORGMEMORY_AUTHORIZATION_OPENFGA_AUTHORIZATION_MODEL_ID'] =
        $config.ORGMEMORY_OPENFGA_AUTHORIZATION_MODEL_ID
    $environment['ORGMEMORY_OBJECT_STORAGE_ENDPOINT'] = 'http://127.0.0.1:19000'
    $environment['SPRING_PROFILES_ACTIVE'] = 'retrieval-observation'
    $environment['SPRING_FLYWAY_ENABLED'] = 'false'
    $environment['ORGMEMORY_GRAPH_RAG_POSTGRES_PROVISION_INDEXES'] = 'false'
    $environment['ORGMEMORY_GRAPH_RAG_POSTGRES_RECONCILE_PUBLISHED_BATCHES'] = 'false'
    $environment['ORGMEMORY_RETRIEVAL_OBSERVATION_EXPECTED_DATABASE'] =
        $RestoredDatabase
    $environment['ORGMEMORY_RETRIEVAL_OBSERVATION_CASES'] =
        [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'demo/fixtures/public-evaluation.json'))
    $environment['ORGMEMORY_RETRIEVAL_OBSERVATION_MANIFEST'] =
        [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'demo/fixtures/documents/manifest.json'))
    $environment['ORGMEMORY_RETRIEVAL_OBSERVATION_OUTPUT'] =
        [System.IO.Path]::GetFullPath((Join-Path $repoRoot $Output))
    $environment['ORGMEMORY_OTLP_METRICS_ENABLED'] = 'false'

    Start-CaptureProcess $environment
    Write-Host "Retrieval observations written to $Output"
}
finally {
    if ($null -ne $tunnel) {
        if (-not $tunnel.HasExited) { $tunnel.Kill($true) }
        $tunnel.Dispose()
    }
    Pop-Location
}
