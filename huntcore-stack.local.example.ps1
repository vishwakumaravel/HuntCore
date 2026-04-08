$HuntCoreStack = @{
    PaperServerDir = "C:\Users\vkper\Downloads\PaperServer"
    BackendProject = "backend-api"
    BackendPort = 8081
    DashboardEnabled = $true
    DashboardPort = 4173
    DashboardApiBaseUrl = "http://127.0.0.1:8081"
    PostgresUrl = "jdbc:postgresql://localhost:5432/huntcore"
    PostgresUser = "postgres"
    PostgresPassword = "replace-me"
    IngestApiKey = "replace-me-with-a-long-random-write-api-key"
    DockerComposeFile = "docker-compose.yml"
    DockerEnvFile = ".env"
    DockerBackendPort = 8081
    DockerDashboardPort = 4173
    DockerBuildOnStart = $true
    DockerStopOnExit = $false
}
