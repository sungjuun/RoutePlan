[CmdletBinding()]
param(
    [string]$ComposeProject = "routeplan",
    [string]$ComposeFile = "compose.yaml"
)

$ErrorActionPreference = "Stop"
$repoDir = Split-Path -Parent $PSScriptRoot
$sqlFile = Join-Path $PSScriptRoot "sample-data/global-routes.sql"

if (-not (Test-Path -LiteralPath $sqlFile -PathType Leaf)) {
    throw "Global sample SQL file was not found: $sqlFile"
}

$postgresContainer = & docker compose -p $ComposeProject -f (Join-Path $repoDir $ComposeFile) `
    ps -q --status running postgres
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($postgresContainer -join ""))) {
    throw "The PostgreSQL Compose service is not running for project '$ComposeProject'."
}

$sql = Get-Content -LiteralPath $sqlFile -Raw -Encoding UTF8
$sql | & docker compose -p $ComposeProject -f (Join-Path $repoDir $ComposeFile) `
    exec -T postgres sh -c 'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
if ($LASTEXITCODE -ne 0) {
    throw "Global sample import failed with exit code $LASTEXITCODE."
}

Write-Output "Global sample import completed: 40 routes, 160 route items."
