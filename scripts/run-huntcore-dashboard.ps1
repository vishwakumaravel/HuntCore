param(
    [Parameter(Mandatory = $true)]
    [string]$DashboardDir,
    [Parameter(Mandatory = $true)]
    [string]$NpmExecutable,
    [Parameter(Mandatory = $true)]
    [string]$LogPath,
    [int]$DashboardPort = 4173,
    [string]$ApiBaseUrl = "http://127.0.0.1:8081"
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $LogPath) | Out-Null
Add-Content -Path $LogPath -Value "Starting dashboard at $(Get-Date -Format s)"
Add-Content -Path $LogPath -Value "Using API base URL $ApiBaseUrl"

try {
    Set-Location $DashboardDir

    $nodeBinDir = Split-Path -Parent $NpmExecutable
    if ($nodeBinDir) {
        $env:Path = "$nodeBinDir;$env:Path"
    }

    $env:VITE_API_BASE_URL = $ApiBaseUrl
    & $NpmExecutable run dev -- --host 127.0.0.1 --port $DashboardPort 2>&1 | Tee-Object -FilePath $LogPath -Append
    exit $LASTEXITCODE
} catch {
    $_ | Out-String | Tee-Object -FilePath $LogPath -Append | Out-Null
    throw
}
