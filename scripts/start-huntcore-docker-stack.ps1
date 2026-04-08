param(
    [string]$PaperServerDir = "C:\Users\vkper\Downloads\PaperServer",
    [string]$ComposeFile = "",
    [string]$EnvFile = "",
    [int]$BackendPort = 0,
    [int]$DashboardPort = 0,
    [switch]$SkipBuild,
    [switch]$StopDockerOnExit
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$localSettingsPath = Join-Path $repoRoot "huntcore-stack.local.ps1"
if (Test-Path $localSettingsPath) {
    . $localSettingsPath

    if ($HuntCoreStack) {
        if ($PaperServerDir -eq "C:\Users\vkper\Downloads\PaperServer" -and $HuntCoreStack.PaperServerDir) {
            $PaperServerDir = $HuntCoreStack.PaperServerDir
        }
        if ([string]::IsNullOrWhiteSpace($ComposeFile) -and $HuntCoreStack.DockerComposeFile) {
            $ComposeFile = $HuntCoreStack.DockerComposeFile
        }
        if ([string]::IsNullOrWhiteSpace($EnvFile) -and $HuntCoreStack.DockerEnvFile) {
            $EnvFile = $HuntCoreStack.DockerEnvFile
        }
        if ($BackendPort -le 0 -and $HuntCoreStack.DockerBackendPort) {
            $BackendPort = [int]$HuntCoreStack.DockerBackendPort
        }
        if ($DashboardPort -le 0 -and $HuntCoreStack.DockerDashboardPort) {
            $DashboardPort = [int]$HuntCoreStack.DockerDashboardPort
        }
        if (-not $SkipBuild -and $null -ne $HuntCoreStack.DockerBuildOnStart) {
            $SkipBuild = -not [bool]$HuntCoreStack.DockerBuildOnStart
        }
        if (-not $StopDockerOnExit -and $null -ne $HuntCoreStack.DockerStopOnExit) {
            $StopDockerOnExit = [bool]$HuntCoreStack.DockerStopOnExit
        }
    }
}

if ([string]::IsNullOrWhiteSpace($ComposeFile)) {
    $ComposeFile = Join-Path $repoRoot "docker-compose.yml"
}
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $repoRoot ".env"
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

$ComposeFile = Resolve-PathIfRelative -PathValue $ComposeFile -BaseDirectory $repoRoot
$EnvFile = Resolve-PathIfRelative -PathValue $EnvFile -BaseDirectory $repoRoot

function Read-DotEnvFile {
    param([string]$Path)

    $values = @{}
    if (-not (Test-Path $Path)) {
        return $values
    }

    foreach ($line in Get-Content -Path $Path) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        if ($line.TrimStart().StartsWith("#")) {
            continue
        }
        $separatorIndex = $line.IndexOf("=")
        if ($separatorIndex -lt 1) {
            continue
        }

        $key = $line.Substring(0, $separatorIndex).Trim()
        $value = $line.Substring($separatorIndex + 1).Trim()
        $values[$key] = $value
    }

    return $values
}

function Test-HttpEndpoint {
    param(
        [string]$Uri,
        [int]$TimeoutSeconds = 2
    )

    try {
        return Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec $TimeoutSeconds
    } catch {
        return $null
    }
}

function Test-BackendHealth {
    param([int]$Port)

    return $null -ne (Test-HttpEndpoint -Uri "http://127.0.0.1:$Port/health")
}

function Test-DashboardReady {
    param([int]$Port)

    $response = Test-HttpEndpoint -Uri "http://127.0.0.1:$Port/"
    return $response -and $response.Content -like "*HuntCore Dashboard*"
}

function Get-HuntCoreConfiguredBackendPort {
    param([string]$PaperDirectory)

    $pluginConfigPath = Join-Path $PaperDirectory "plugins\HuntCore\config.yml"
    if (-not (Test-Path $pluginConfigPath)) {
        return $null
    }

    $baseUrlLine = Get-Content -Path $pluginConfigPath |
        Where-Object { $_ -match '^\s*base-url:\s*".+"$' -or $_ -match "^\s*base-url:\s*'.+'$" -or $_ -match '^\s*base-url:\s*\S+$' } |
        Select-Object -First 1
    if (-not $baseUrlLine) {
        return $null
    }

    $baseUrl = ($baseUrlLine -replace '^\s*base-url:\s*', '').Trim().Trim('"').Trim("'")
    if (-not $baseUrl) {
        return $null
    }

    try {
        $uri = [System.Uri]$baseUrl
        if ($uri.Port -gt 0) {
            return $uri.Port
        }
    } catch {
        return $null
    }

    return $null
}

function Invoke-DockerCompose {
    param(
        [string[]]$Arguments,
        [switch]$PassThruOutput
    )

    $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $dockerCommand) {
        throw "Docker is not installed or not on PATH. Install Docker Desktop before using the Docker launcher."
    }

    $composeArgs = @("compose", "--env-file", $EnvFile, "-f", $ComposeFile) + $Arguments
    if ($PassThruOutput) {
        & $dockerCommand.Source @composeArgs | Out-Host
        $composeExitCode = $LASTEXITCODE
        return $composeExitCode
    }

    & $dockerCommand.Source @composeArgs | Out-Null
    $composeExitCode = $LASTEXITCODE
    return $composeExitCode
}

function Wait-Until {
    param(
        [scriptblock]$Condition,
        [int]$Attempts,
        [int]$DelaySeconds
    )

    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        if (& $Condition) {
            return $true
        }
        Start-Sleep -Seconds $DelaySeconds
    }

    return $false
}

$composeExampleFile = Join-Path $repoRoot ".env.example"
if (-not (Test-Path $EnvFile)) {
    if (Test-Path $composeExampleFile) {
        Copy-Item $composeExampleFile $EnvFile
        throw "Created $EnvFile from .env.example. Fill in the real values, save it, and run start-huntcore-docker.bat again."
    }

    throw "Could not find $EnvFile. Create it before using the Docker launcher."
}

if (-not (Test-Path $ComposeFile)) {
    throw "Could not find Docker Compose file at $ComposeFile"
}

$paperStartScript = Join-Path $PaperServerDir "start.bat"
if (-not (Test-Path $paperStartScript)) {
    throw "Could not find Paper start script at $paperStartScript"
}

$envValues = Read-DotEnvFile -Path $EnvFile
if ($BackendPort -le 0) {
    $BackendPort = if ($envValues.ContainsKey("BACKEND_PORT")) { [int]$envValues["BACKEND_PORT"] } else { 8081 }
}
if ($DashboardPort -le 0) {
    $DashboardPort = if ($envValues.ContainsKey("DASHBOARD_PORT")) { [int]$envValues["DASHBOARD_PORT"] } else { 4173 }
}

$configuredBackendPort = Get-HuntCoreConfiguredBackendPort -PaperDirectory $PaperServerDir
if ($configuredBackendPort -and $configuredBackendPort -ne $BackendPort) {
    Write-Warning "Paper is configured to use backend port $configuredBackendPort, but Docker is exposing backend port $BackendPort. Update either plugins\\HuntCore\\config.yml or .env so they match."
}

$dockerInfo = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerInfo) {
    throw "Docker is not installed or not on PATH. Install Docker Desktop before using the Docker launcher."
}

& $dockerInfo.Source info | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Docker Desktop does not appear to be running. Start Docker Desktop and try again."
}

$upArgs = @("up", "-d")
if (-not $SkipBuild) {
    $upArgs += "--build"
}

Write-Host "Starting Docker services..."
$exitCode = Invoke-DockerCompose -Arguments $upArgs -PassThruOutput
if ($exitCode -ne 0) {
    throw "docker compose up failed with exit code $exitCode"
}

$backendReady = Wait-Until -Attempts 45 -DelaySeconds 2 -Condition { Test-BackendHealth -Port $BackendPort }
if (-not $backendReady) {
    Write-Warning "Backend did not report healthy before Paper launch. Check 'docker compose logs backend-api' if Paper cannot sync."
} else {
    Write-Host "Docker backend is up on http://127.0.0.1:$BackendPort"
}

$dashboardReady = Wait-Until -Attempts 30 -DelaySeconds 1 -Condition { Test-DashboardReady -Port $DashboardPort }
if (-not $dashboardReady) {
    Write-Warning "Dashboard did not report ready before Paper launch. Check 'docker compose logs dashboard' if the website does not load."
} else {
    Write-Host "Docker dashboard is up on http://127.0.0.1:$DashboardPort"
}

Write-Host "Launching Paper from $PaperServerDir..."
Push-Location $PaperServerDir
try {
    & $paperStartScript
}
finally {
    Pop-Location
}

if ($StopDockerOnExit) {
    Write-Host "Stopping Docker services..."
    $stopExitCode = Invoke-DockerCompose -Arguments @("stop") -PassThruOutput
    if ($stopExitCode -ne 0) {
        Write-Warning "docker compose stop exited with code $stopExitCode"
    }
} else {
    Write-Host "Docker services are still running."
    Write-Host "Use stop-huntcore-docker.bat when you want to stop the Docker stack."
}
