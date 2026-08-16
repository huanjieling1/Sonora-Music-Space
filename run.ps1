$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
. "$PSScriptRoot\scripts\runtime-support.ps1"

Import-SonoraEnvironment -ProjectRoot $PSScriptRoot
Enable-SonoraJava17
Assert-SonoraMySql
Start-SonoraNeo4j -ProjectRoot $PSScriptRoot
Start-SonoraQqBridge -ProjectRoot $PSScriptRoot

if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    Write-Host 'Spring Boot is already running on port 8080; no duplicate process was started.' `
        -ForegroundColor DarkGreen
    exit 0
}

.\mvnw.cmd spring-boot:run
