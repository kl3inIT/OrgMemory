[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z0-9.-]+$')]
    [string]$Server,

    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z0-9._-]+$')]
    [string]$SshUser,

    [ValidateRange(1, 65535)]
    [int]$SshPort = 22,

    [ValidateSet('api', 'web', 'worker', 'mcp')]
    [ValidateCount(1, 4)]
    [string[]]$Services = @('api', 'web')
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$sshTarget = "$SshUser@$Server"
$sessionId = "local-$([guid]::NewGuid().ToString('N'))"
$processes = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()
$workerLease = $false

function Invoke-SshText {
    param([Parameter(Mandatory)][string]$RemoteCommand)
    $result = & ssh.exe -o BatchMode=yes -o ConnectTimeout=10 -p $SshPort $sshTarget $RemoteCommand
    if ($LASTEXITCODE -ne 0) {
        throw "SSH command failed with exit code $LASTEXITCODE."
    }
    return ($result -join "`n")
}

function Start-ChildProcess {
    param(
        [Parameter(Mandatory)][string]$FileName,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][hashtable]$Environment
    )
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FileName
    $startInfo.WorkingDirectory = $repoRoot
    $startInfo.UseShellExecute = $false
    foreach ($argument in $Arguments) {
        [void]$startInfo.ArgumentList.Add($argument)
    }
    foreach ($entry in $Environment.GetEnumerator()) {
        $startInfo.Environment[$entry.Key] = [string]$entry.Value
    }
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw "Could not start $FileName."
    }
    $processes.Add($process)
    return $process
}

function Assert-LocalPortFree {
    param([int]$Port)
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
    try { $listener.Start() } finally { $listener.Stop() }
}

Push-Location $repoRoot
try {
    if (($Services -contains 'web' -or $Services -contains 'mcp') -and $Services -notcontains 'api') {
        throw 'Local web and MCP sessions require the local API service. Add api to -Services.'
    }
    if ($Services -contains 'web') {
        $nodeMajor = [int]((& node.exe --version).TrimStart('v').Split('.')[0])
        if ($nodeMajor -ne 24) {
            throw "Node 24 is required; current version is $(& node.exe --version). Run 'nvm use 24'."
        }
    }

    & git fetch origin main --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Could not fetch origin/main.' }
    & python scripts/check_shared_schema_changes.py --base-ref origin/main
    if ($LASTEXITCODE -ne 0) { throw 'Shared schema check failed.' }
    foreach ($port in @(15432, 18081, 19000)) { Assert-LocalPortFree $port }
    $json = Invoke-SshText 'cd /apps/orgmemory && ./infrastructure/deployment/scripts/export-team-dev-config.sh'
    $config = $json | ConvertFrom-Json -AsHashtable

    $tunnelArguments = @(
        '-N', '-o', 'BatchMode=yes', '-o', 'ExitOnForwardFailure=yes',
        '-o', 'ServerAliveInterval=30', '-o', 'ServerAliveCountMax=3',
        '-p', "$SshPort",
        '-L', "127.0.0.1:15432:$($config.postgresTarget)",
        '-L', "127.0.0.1:18081:$($config.openfgaTarget)",
        '-L', "127.0.0.1:19000:$($config.minioTarget)",
        $sshTarget
    )
    $tunnel = Start-ChildProcess 'ssh.exe' $tunnelArguments @{}
    Start-Sleep -Seconds 1
    if ($tunnel.HasExited) { throw 'SSH tunnel exited before the applications started.' }

    $environment = @{}
    foreach ($entry in $config.GetEnumerator()) {
        if ($entry.Key -like 'ORGMEMORY_*' -and -not [string]::IsNullOrEmpty([string]$entry.Value)) {
            $environment[$entry.Key] = $entry.Value
        }
    }
    $environment['ORGMEMORY_DB_URL'] = "jdbc:postgresql://127.0.0.1:15432/$($config.ORGMEMORY_DB_NAME)"
    $environment['ORGMEMORY_OPENFGA_API_URL'] = 'http://127.0.0.1:18081'
    $environment['ORGMEMORY_AUTHORIZATION_OPENFGA_API_URL'] = 'http://127.0.0.1:18081'
    $environment['ORGMEMORY_AUTHORIZATION_OPENFGA_STORE_ID'] = $config.ORGMEMORY_OPENFGA_STORE_ID
    $environment['ORGMEMORY_AUTHORIZATION_OPENFGA_AUTHORIZATION_MODEL_ID'] = $config.ORGMEMORY_OPENFGA_AUTHORIZATION_MODEL_ID
    $environment['ORGMEMORY_OBJECT_STORAGE_ENDPOINT'] = 'http://127.0.0.1:19000'
    $environment['ORGMEMORY_OIDC_ISSUER_URI'] = 'https://auth.kl3in.tech/realms/orgmemory'
    $environment['ORGMEMORY_OIDC_CLIENT_ID'] = 'orgmemory-web'
    $environment['ORGMEMORY_WEB_BASE_URL'] = 'http://127.0.0.1:5173'
    $environment['ORGMEMORY_API_BASE_URL'] = 'http://127.0.0.1:8080'
    $environment['ORGMEMORY_SESSION_COOKIE_SECURE'] = 'false'
    $environment['SPRING_FLYWAY_ENABLED'] = 'false'
    $environment['ORGMEMORY_GRAPH_RAG_POSTGRES_PROVISION_INDEXES'] = 'false'
    $environment['ORGMEMORY_DEPLOYMENT_ENVIRONMENT'] = 'shared-zm-local'
    $environment['ORGMEMORY_OTLP_METRICS_ENABLED'] = 'false'

    if ($Services -contains 'worker') {
        $commit = (& git rev-parse --short=12 HEAD).Trim()
        $owner = $env:USERNAME -replace '[^A-Za-z0-9._@:-]', '-'
        $hostName = $env:COMPUTERNAME -replace '[^A-Za-z0-9._@:-]', '-'
        if ([string]::IsNullOrWhiteSpace($owner)) { $owner = 'developer' }
        if ([string]::IsNullOrWhiteSpace($hostName)) { $hostName = 'workstation' }
        Invoke-SshText "cd /apps/orgmemory && ./infrastructure/deployment/scripts/team-dev-coordination.sh acquire worker $sessionId $owner $hostName $commit 300" | Write-Host
        $workerLease = $true
    }

    if ($Services -contains 'api') {
        [void](Start-ChildProcess 'cmd.exe' @('/d', '/c', "$repoRoot\gradlew.bat", '--no-daemon', ':apps:api:bootRun') $environment)
    }
    if ($Services -contains 'worker') {
        [void](Start-ChildProcess 'cmd.exe' @('/d', '/c', "$repoRoot\gradlew.bat", '--no-daemon', ':apps:worker:bootRun') $environment)
    }
    if ($Services -contains 'mcp') {
        [void](Start-ChildProcess 'cmd.exe' @('/d', '/c', "$repoRoot\gradlew.bat", '--no-daemon', ':apps:mcp:bootRun') $environment)
    }
    if ($Services -contains 'web') {
        $webEnvironment = @{ VITE_API_ORIGIN = 'http://127.0.0.1:8080'; VITE_API_PROXY_TARGET = 'http://127.0.0.1:8080' }
        [void](Start-ChildProcess 'cmd.exe' @('/d', '/c', 'corepack.cmd', 'pnpm', '--filter', '@orgmemory/web', 'dev', '--', '--host', '127.0.0.1') $webEnvironment)
    }

    Write-Host "Shared ZM development is running: $($Services -join ', '). Press Ctrl+C to stop."
    $nextHeartbeat = [DateTime]::UtcNow.AddMinutes(1)
    while (($processes | Where-Object { -not $_.HasExited }).Count -gt 1) {
        Start-Sleep -Seconds 2
        if ($workerLease -and [DateTime]::UtcNow -ge $nextHeartbeat) {
            Invoke-SshText "cd /apps/orgmemory && ./infrastructure/deployment/scripts/team-dev-coordination.sh heartbeat worker $sessionId 300" | Out-Null
            $nextHeartbeat = [DateTime]::UtcNow.AddMinutes(1)
        }
    }
}
finally {
    if ($workerLease) {
        try { Invoke-SshText "cd /apps/orgmemory && ./infrastructure/deployment/scripts/team-dev-coordination.sh release worker $sessionId" | Write-Host } catch { Write-Warning $_ }
    }
    foreach ($process in $processes) {
        if (-not $process.HasExited) { $process.Kill($true) }
        $process.Dispose()
    }
    Pop-Location
}
