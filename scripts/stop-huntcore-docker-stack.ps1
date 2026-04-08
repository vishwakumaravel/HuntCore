param(
    [string]$ComposeFile = "",
    [string]$EnvFile = "",
    [switch]$Down
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$localSettingsPath = Join-Path $repoRoot "huntcore-stack.local.ps1"
if (Test-Path $localSettingsPath) {
    . $localSettingsPath

    if ($HuntCoreStack) {
        if ([string]::IsNullOrWhiteSpace($ComposeFile) -and $HuntCoreStack.DockerComposeFile) {
            $ComposeFile = $HuntCoreStack.DockerComposeFile
        }
        if ([string]::IsNullOrWhiteSpace($EnvFile) -and $HuntCoreStack.DockerEnvFile) {
            $EnvFile = $HuntCoreStack.DockerEnvFile
        }
    }
}

function Resolve-PathIfRelative {
    param(
        [string]$PathValue,
        [string]$BaseDirectory
    )

    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        return $PathValue
    }

    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return $PathValue
    }

    return Join-Path $BaseDirectory $PathValue
}

if ([string]::IsNullOrWhiteSpace($ComposeFile)) {
    $ComposeFile = Join-Path $repoRoot "docker-compose.yml"
}
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $repoRoot ".env"
}

$ComposeFile = Resolve-PathIfRelative -PathValue $ComposeFile -BaseDirectory $repoRoot
$EnvFile = Resolve-PathIfRelative -PathValue $EnvFile -BaseDirectory $repoRoot

if (-not (Test-Path $ComposeFile)) {
    throw "Could not find Docker Compose file at $ComposeFile"
}

$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCommand) {
    throw "Docker is not installed or not on PATH."
}

$composeArgs = @("compose", "--env-file", $EnvFile, "-f", $ComposeFile)
if ($Down) {
    $composeArgs += "down"
    Write-Host "Bringing down Docker services..."
} else {
    $composeArgs += "stop"
    Write-Host "Stopping Docker services..."
}

& $dockerCommand.Source @composeArgs
if ($LASTEXITCODE -ne 0) {
    throw "docker compose exited with code $LASTEXITCODE"
}
