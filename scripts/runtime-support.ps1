$ErrorActionPreference = 'Stop'

function Import-SonoraEnvironment {
    param([Parameter(Mandatory)][string]$ProjectRoot)

    $environmentFile = Join-Path $ProjectRoot '.env'
    if (-not (Test-Path -LiteralPath $environmentFile)) {
        throw 'Missing .env. Copy .env.example to .env and fill the local configuration.'
    }
    foreach ($line in Get-Content -LiteralPath $environmentFile) {
        if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') { continue }
        $name = $Matches[1]
        $value = $Matches[2].Trim().Trim('"').Trim("'")
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }

    $userBacked = @(
        'NEO4J_ENABLED', 'NEO4J_HOME', 'NEO4J_URI', 'NEO4J_USERNAME', 'NEO4J_PASSWORD',
        'NEO4J_DATABASE', 'AGENT_EMBEDDING_MODEL', 'AGENT_EMBEDDING_DIMENSIONS'
    )
    foreach ($name in $userBacked) {
        if (-not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            continue
        }
        $value = [Environment]::GetEnvironmentVariable($name, 'User')
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}

function Enable-SonoraJava17 {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        $temurin = Get-ChildItem -Path 'C:\Program Files\Eclipse Adoptium' -Directory `
            -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | Select-Object -First 1
        if ($temurin) {
            $env:JAVA_HOME = $temurin.FullName
            $env:Path = "$($temurin.FullName)\bin;$env:Path"
        }
    }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw 'Java 17 was not found. Install Temurin JDK 17 first.'
    }
}

function Test-TcpEndpoint {
    param([Parameter(Mandatory)][string]$HostName, [Parameter(Mandatory)][int]$Port)
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync($HostName, $Port)
        return $task.Wait(2000) -and $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Assert-SonoraMySql {
    if ($env:DB_URL -notmatch '^jdbc:mysql://([^/:?]+)(?::(\d+))?') {
        throw 'DB_URL is not a valid MySQL JDBC URL.'
    }
    $databaseHost = $Matches[1]
    $databasePort = if ($Matches[2]) { [int]$Matches[2] } else { 3306 }
    if (-not (Test-TcpEndpoint -HostName $databaseHost -Port $databasePort)) {
        throw "MySQL is not reachable at $databaseHost`:$databasePort. Start MySQL before Sonora."
    }
    Write-Host "MySQL ready at $databaseHost`:$databasePort" -ForegroundColor DarkGreen
}

function Get-LoopbackListener {
    param([Parameter(Mandatory)][int]$Port)
    return Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort $Port -State Listen `
        -ErrorAction SilentlyContinue | Select-Object -First 1
}

function Wait-LoopbackListener {
    param([Parameter(Mandatory)][int]$Port, [int]$TimeoutSeconds = 30)
    for ($attempt = 0; $attempt -lt $TimeoutSeconds; $attempt++) {
        if (Get-LoopbackListener -Port $Port) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Wait-LocalListener {
    param([Parameter(Mandatory)][int]$Port, [int]$TimeoutSeconds = 30)
    for ($attempt = 0; $attempt -lt $TimeoutSeconds; $attempt++) {
        if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
            return $true
        }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Start-SonoraNeo4j {
    param([Parameter(Mandatory)][string]$ProjectRoot)
    if ($env:NEO4J_ENABLED -and $env:NEO4J_ENABLED -eq 'false') {
        Write-Host 'Neo4j disabled; catalog fallback will remain available.' -ForegroundColor DarkYellow
        return
    }
    $neo4jHome = if ($env:NEO4J_HOME) { $env:NEO4J_HOME } else {
        Join-Path $ProjectRoot 'runtime-tools\neo4j'
    }
    $launcher = Join-Path $neo4jHome 'bin\neo4j.bat'
    if (-not (Test-Path -LiteralPath $launcher)) {
        throw "Neo4j 5.26 runtime is missing at $neo4jHome."
    }
    if ([string]::IsNullOrWhiteSpace($env:NEO4J_PASSWORD)) {
        throw 'NEO4J_PASSWORD is not configured. Run the local Neo4j setup first.'
    }
    $unsafeListener = Get-NetTCPConnection -LocalPort 7687 -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalAddress -ne '127.0.0.1' }
    if ($unsafeListener) {
        throw 'Port 7687 is listening outside 127.0.0.1; refusing to start the local graph database.'
    }
    if (-not (Get-LoopbackListener -Port 7687)) {
        $env:NEO4J_HOME = $neo4jHome
        Start-Process -FilePath $launcher -ArgumentList 'console' -WorkingDirectory $neo4jHome `
            -WindowStyle Hidden | Out-Null
        if (-not (Wait-LoopbackListener -Port 7687)) {
            throw 'Neo4j did not become ready on 127.0.0.1:7687 within 30 seconds.'
        }
    }
    Write-Host 'Neo4j ready at 127.0.0.1:7687' -ForegroundColor DarkGreen
}

function Start-SonoraQqBridge {
    param([Parameter(Mandatory)][string]$ProjectRoot)
    $unsafeListener = Get-NetTCPConnection -LocalPort 3200 -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalAddress -ne '127.0.0.1' }
    if ($unsafeListener) {
        throw 'Port 3200 is listening outside 127.0.0.1; refusing to use it for QQ credentials.'
    }
    if (Get-LoopbackListener -Port 3200) {
        Write-Host 'QQ Bridge already running at 127.0.0.1:3200' -ForegroundColor DarkGreen
        return
    }
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
        throw 'Node.js was not found; QQ Bridge cannot start.'
    }
    $bridgeDirectory = Join-Path $ProjectRoot 'integrations\qq-music-bridge'
    Start-Process -FilePath (Get-Command node).Source -ArgumentList 'server.js' `
        -WorkingDirectory $bridgeDirectory -WindowStyle Hidden | Out-Null
    if (-not (Wait-LoopbackListener -Port 3200 -TimeoutSeconds 15)) {
        throw 'QQ Bridge did not become ready on 127.0.0.1:3200.'
    }
    Write-Host 'QQ Bridge ready at 127.0.0.1:3200' -ForegroundColor DarkGreen
}
