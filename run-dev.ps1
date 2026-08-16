$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
. "$PSScriptRoot\scripts\runtime-support.ps1"

Import-SonoraEnvironment -ProjectRoot $PSScriptRoot
Enable-SonoraJava17
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) { throw 'Node.js and npm were not found.' }
Assert-SonoraMySql
Start-SonoraNeo4j -ProjectRoot $PSScriptRoot
Start-SonoraQqBridge -ProjectRoot $PSScriptRoot

$backend = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $backend) {
    Start-Process -FilePath "$PSScriptRoot\mvnw.cmd" -ArgumentList 'spring-boot:run' `
        -WorkingDirectory $PSScriptRoot -WindowStyle Hidden | Out-Null
    if (-not (Wait-LocalListener -Port 8080 -TimeoutSeconds 45)) {
        throw 'Spring Boot did not become ready on port 8080 within 45 seconds.'
    }
}
Write-Host 'Spring Boot ready at http://127.0.0.1:8080' -ForegroundColor DarkGreen

Set-Location -LiteralPath "$PSScriptRoot\frontend"
if (-not (Test-Path -LiteralPath '.\node_modules')) {
    npm install
}
if (Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue) {
    Write-Host 'Vue already running at http://127.0.0.1:5173' -ForegroundColor DarkGreen
    exit 0
}
npm run dev
