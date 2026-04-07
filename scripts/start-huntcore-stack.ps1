param(
    [string]$PaperServerDir = "C:\Users\vkper\Downloads\PaperServer",
    [ValidateSet("backend-api", "backend-stub")]
    [string]$BackendProject = "backend-api",
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$BackendPort = 8080,
    [string]$PostgresUrl = "jdbc:postgresql://localhost:5432/huntcore",
    [string]$PostgresUser = "huntcore",
    [string]$PostgresPassword = "huntcore",
    [string]$IngestApiKey = "",
    [switch]$ShowBackendWindow
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradleExecutable = Join-Path $repoRoot ".gradle-temp\gradle-8.10.2\bin\gradle.bat"
if (-not (Test-Path $gradleExecutable)) {
    $gradleExecutable = Join-Path $repoRoot "gradlew.bat"
}
$localSettingsPath = Join-Path $repoRoot "huntcore-stack.local.ps1"
if (Test-Path $localSettingsPath) {
    . $localSettingsPath

    if ($HuntCoreStack) {
        if ($PaperServerDir -eq "C:\Users\vkper\Downloads\PaperServer" -and $HuntCoreStack.PaperServerDir) {
            $PaperServerDir = $HuntCoreStack.PaperServerDir
        }
        if ($BackendProject -eq "backend-api" -and $HuntCoreStack.BackendProject) {
            $BackendProject = $HuntCoreStack.BackendProject
        }
        if (-not $JavaHome -and $HuntCoreStack.JavaHome) {
            $JavaHome = $HuntCoreStack.JavaHome
        }
        if ($BackendPort -eq 8080 -and $HuntCoreStack.BackendPort) {
            $BackendPort = [int]$HuntCoreStack.BackendPort
        }
        if ($PostgresUrl -eq "jdbc:postgresql://localhost:5432/huntcore" -and $HuntCoreStack.PostgresUrl) {
            $PostgresUrl = $HuntCoreStack.PostgresUrl
        }
        if ($PostgresUser -eq "huntcore" -and $HuntCoreStack.PostgresUser) {
            $PostgresUser = $HuntCoreStack.PostgresUser
        }
        if ($PostgresPassword -eq "huntcore" -and $HuntCoreStack.PostgresPassword) {
            $PostgresPassword = $HuntCoreStack.PostgresPassword
        }
        if ($IngestApiKey -eq "" -and $HuntCoreStack.IngestApiKey) {
            $IngestApiKey = $HuntCoreStack.IngestApiKey
        }
    }
}

function Resolve-JavaHome {
    param([string]$ConfiguredJavaHome)

    if ($ConfiguredJavaHome -and (Test-Path (Join-Path $ConfiguredJavaHome "bin\java.exe"))) {
        return $ConfiguredJavaHome
    }

    $candidatePatterns = @(
        (Join-Path $env:USERPROFILE ".vscode\extensions\redhat.java-*\jre\21*"),
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\Microsoft\jdk-21*",
        "C:\Program Files\Java\jdk-21*"
    )

    foreach ($pattern in $candidatePatterns) {
        $match = Get-ChildItem -Path $pattern -Directory -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($match -and (Test-Path (Join-Path $match.FullName "bin\java.exe"))) {
            return $match.FullName
        }
    }

    return $null
}

function Test-BackendHealth {
    param([int]$Port)

    try {
        $null = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 2
        return $true
    } catch {
        return $false
    }
}

function Get-PortConflictDescription {
    param([int]$Port)

    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 2
        $serverHeader = $response.Headers["Server"]
        if ($serverHeader) {
            return "Port $Port is already responding with server '$serverHeader'."
        }
        return "Port $Port is already responding to HTTP requests."
    } catch {
        $exception = $_.Exception
        $response = $exception.Response
        if ($response) {
            $serverHeader = $response.Headers["Server"]
            if ($serverHeader) {
                return "Port $Port is already in use by an HTTP server that identifies as '$serverHeader'."
            }

            return "Port $Port is already in use by an HTTP server."
        }

        return $null
    }
}

function Test-PostgresReachable {
    param([string]$JdbcUrl)

    if ($JdbcUrl -notmatch "^jdbc:postgresql://([^:/]+)(?::(\d+))?/") {
        return $false
    }

    $hostName = $matches[1]
    $port = if ($matches[2]) { [int]$matches[2] } else { 5432 }

    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $connectAsync = $client.ConnectAsync($hostName, $port)
        $connected = $connectAsync.Wait(1500)
        if (-not $connected) {
            $client.Dispose()
            return $false
        }

        $isConnected = $client.Connected
        $client.Dispose()
        return $isConnected
    } catch {
        return $false
    }
}

function Try-StartPostgresService {
    $postgresService = Get-Service |
        Where-Object { $_.Name -like "postgresql*" -or $_.DisplayName -like "PostgreSQL*" } |
        Sort-Object Name |
        Select-Object -First 1

    if (-not $postgresService) {
        return $false
    }

    if ($postgresService.Status -eq "Running") {
        return $true
    }

    try {
        Start-Service -Name $postgresService.Name -ErrorAction Stop
        Start-Sleep -Seconds 2
        return $true
    } catch {
        Write-Warning "Found PostgreSQL service '$($postgresService.Name)' but could not start it automatically."
        return $false
    }
}

function Get-BackendApiJar {
    param([string]$ProjectDir)

    return Get-ChildItem -Path (Join-Path $ProjectDir "build\libs\*.jar") -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-plain.jar" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

function Start-ManagedBackendProcess {
    param(
        [string]$ProjectName,
        [string]$ProjectDir,
        [string]$ResolvedJavaHome
    )

    $logDir = Join-Path $repoRoot "logs"
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $backendLogPath = Join-Path $logDir "$ProjectName.log"
    $backendRunnerScript = Join-Path $PSScriptRoot "run-huntcore-backend.ps1"

    $argumentList = @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        $backendRunnerScript,
        "-ProjectName",
        $ProjectName,
        "-ProjectDir",
        $ProjectDir,
        "-JavaHome",
        $ResolvedJavaHome,
        "-LogPath",
        $backendLogPath,
        "-BackendPort",
        "$BackendPort"
    )

    if ($ProjectName -eq "backend-api") {
        $argumentList += @(
            "-PostgresUrl",
            $PostgresUrl,
            "-PostgresUser",
            $PostgresUser,
            "-PostgresPassword",
            $PostgresPassword
        )
    }

    if (-not [string]::IsNullOrWhiteSpace($IngestApiKey)) {
        $argumentList += @(
            "-IngestApiKey",
            $IngestApiKey
        )
    }

    $startProcessArgs = @{
        FilePath = "powershell.exe"
        WorkingDirectory = $ProjectDir
        ArgumentList = $argumentList
        PassThru = $true
    }

    if (-not $ShowBackendWindow) {
        $startProcessArgs.WindowStyle = "Hidden"
    }

    return Start-Process @startProcessArgs
}

function Stop-ManagedProcessTree {
    param(
        [System.Diagnostics.Process]$Process,
        [string]$Label
    )

    if (-not $Process) {
        return
    }

    try {
        if ($Process.HasExited) {
            return
        }
    } catch {
        return
    }

    Write-Host "Stopping $Label..."
    Start-Process -FilePath "taskkill.exe" -ArgumentList "/PID", $Process.Id, "/T", "/F" -NoNewWindow -Wait | Out-Null
}

$resolvedJavaHome = Resolve-JavaHome -ConfiguredJavaHome $JavaHome
if (-not $resolvedJavaHome) {
    throw "Set -JavaHome or JAVA_HOME to a Java 21 installation before using this script."
}

$paperStartScript = Join-Path $PaperServerDir "start.bat"
if (-not (Test-Path $paperStartScript)) {
    throw "Could not find Paper start script at $paperStartScript"
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

$configuredBackendPort = Get-HuntCoreConfiguredBackendPort -PaperDirectory $PaperServerDir
if ($configuredBackendPort) {
    $BackendPort = $configuredBackendPort
}

if ($BackendProject -eq "backend-api" -and -not (Test-PostgresReachable -JdbcUrl $PostgresUrl)) {
    $postgresStarted = Try-StartPostgresService
    if ($postgresStarted) {
        Start-Sleep -Seconds 2
    }

    if (-not (Test-PostgresReachable -JdbcUrl $PostgresUrl)) {
        Write-Warning "PostgreSQL was not reachable at $PostgresUrl. The backend may fail to start until PostgreSQL is running."
    }
}

$backendProcess = $null
$startedBackendHere = $false

try {
    if (Test-BackendHealth -Port $BackendPort) {
        Write-Host "Backend already responding on port $BackendPort. Reusing it."
    } else {
        $portConflictDescription = Get-PortConflictDescription -Port $BackendPort
        if ($portConflictDescription) {
            throw "$portConflictDescription Stop that service or use a different backend port in both the launcher and plugins/HuntCore/config.yml."
        }

        $backendProjectDir = Join-Path $repoRoot $BackendProject
        $backendProcess = Start-ManagedBackendProcess -ProjectName $BackendProject -ProjectDir $backendProjectDir -ResolvedJavaHome $resolvedJavaHome
        $startedBackendHere = $true

        $backendReady = $false
        for ($attempt = 0; $attempt -lt 45; $attempt++) {
            Start-Sleep -Seconds 1
            if (Test-BackendHealth -Port $BackendPort) {
                $backendReady = $true
                break
            }
        }

        if ($backendReady) {
            Write-Host "Backend is up on port $BackendPort."
        } else {
            Write-Warning "Backend did not report healthy before Paper launch. Paper will still start. Check logs\$BackendProject.log for backend output."
        }
    }

    Write-Host "Launching Paper from $PaperServerDir..."
    Push-Location $PaperServerDir
    try {
        & $paperStartScript
    }
    finally {
        Pop-Location
    }
}
finally {
    if ($startedBackendHere) {
        Stop-ManagedProcessTree -Process $backendProcess -Label $BackendProject
    }
}
