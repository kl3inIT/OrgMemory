param(
    [string]$ApiUrl = "http://localhost:8081"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$cli = Join-Path $repoRoot ".tools\openfga\fga.exe"
$model = Join-Path $repoRoot "integrations\authorization-openfga\src\main\openfga\model.fga"
$tupleFiles = @(
    (Join-Path $repoRoot "integrations\authorization-openfga\src\main\openfga\local-demo-tuples.csv"),
    (Join-Path $repoRoot "demo\fixtures\openfga\dataset-tuples.csv")
)
$output = Join-Path $repoRoot ".openfga.local.properties"

if (-not (Test-Path -LiteralPath $cli)) {
    & (Join-Path $PSScriptRoot "install-openfga-cli.ps1")
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $cli)) {
        throw "OpenFGA CLI installation failed."
    }
}

$response = & $cli store create `
    --api-url $ApiUrl `
    --name "orgmemory-local" `
    --model $model `
    --format fga | ConvertFrom-Json

if ($LASTEXITCODE -ne 0) {
    throw "OpenFGA store creation failed."
}

$storeId = $response.store.id
$modelId = $response.model.authorization_model_id
if ([string]::IsNullOrWhiteSpace($storeId) -or [string]::IsNullOrWhiteSpace($modelId)) {
    throw "OpenFGA CLI returned no store or authorization model id."
}

foreach ($tupleFile in $tupleFiles) {
    if (-not (Test-Path -LiteralPath $tupleFile)) {
        throw "OpenFGA tuple fixture does not exist: $tupleFile"
    }

    & $cli tuple write `
        --api-url $ApiUrl `
        --store-id $storeId `
        --model-id $modelId `
        --file $tupleFile `
        --on-duplicate ignore `
        --hide-imported-tuples

    if ($LASTEXITCODE -ne 0) {
        throw "OpenFGA relationship import failed for $tupleFile."
    }
}

$configuration = @(
    "orgmemory.authorization.openfga.api-url=$ApiUrl"
    "orgmemory.authorization.openfga.store-id=$storeId"
    "orgmemory.authorization.openfga.authorization-model-id=$modelId"
)
[System.IO.File]::WriteAllLines(
    $output,
    $configuration,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Host "OpenFGA local store created."
Write-Host "Store ID: $storeId"
Write-Host "Authorization model ID: $modelId"
Write-Host "Configuration: $output"
