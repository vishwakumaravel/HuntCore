param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("backend-api", "backend-stub")]
    [string]$ProjectName,
    [Parameter(Mandatory = $true)]
    [string]$ProjectDir,
    [Parameter(Mandatory = $true)]
    [string]$JavaHome,
    [Parameter(Mandatory = $true)]
    [string]$LogPath,
    [int]$BackendPort = 8080,
    [string]$PostgresUrl = "jdbc:postgresql://localhost:5432/huntcore",
    [string]$PostgresUser = "huntcore",
    [string]$PostgresPassword = "huntcore",
    [string]$IngestApiKey = ""
)

$ErrorActionPreference = "Stop"

function Get-BackendApiJar {
    param([string]$BackendProjectDir)

    return Get-ChildItem -Path (Join-Path $BackendProjectDir "build\libs\*.jar") -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-plain.jar" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $LogPath) | Out-Null
Add-Content -Path $LogPath -Value "Starting $ProjectName at $(Get-Date -Format s)"

try {
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;$env:Path"
    $env:SERVER_PORT = "$BackendPort"

    if ($ProjectName -eq "backend-api") {
        $env:SPRING_DATASOURCE_URL = $PostgresUrl
        $env:SPRING_DATASOURCE_USERNAME = $PostgresUser
        $env:SPRING_DATASOURCE_PASSWORD = $PostgresPassword
        $env:HUNTCORE_INGEST_API_KEY = $IngestApiKey

        $backendJar = Get-BackendApiJar -BackendProjectDir $ProjectDir
        if (-not $backendJar) {
            throw "Could not find a built backend-api jar in $ProjectDir\build\libs. Build backend-api first."
        }

        Add-Content -Path $LogPath -Value "Launching jar $($backendJar.FullName)"
        & (Join-Path $JavaHome "bin\java.exe") -jar $backendJar.FullName 2>&1 | Tee-Object -FilePath $LogPath -Append
        exit $LASTEXITCODE
    }

    $env:HUNTCORE_API_KEY = $IngestApiKey
    $gradleExecutable = Join-Path (Split-Path -Parent $ProjectDir) "gradlew.bat"
    Add-Content -Path $LogPath -Value "Launching backend-stub via $gradleExecutable"
    & $gradleExecutable -p $ProjectDir run 2>&1 | Tee-Object -FilePath $LogPath -Append
    exit $LASTEXITCODE
} catch {
    $_ | Out-String | Tee-Object -FilePath $LogPath -Append | Out-Null
    throw
}
