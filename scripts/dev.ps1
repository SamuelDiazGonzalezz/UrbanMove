[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('help', 'up', 'down', 'logs', 'ps', 'reset', 'build', 'test', 'verify', 'seed', 'clean')]
    [string]$Command = 'help'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root

try {
    switch ($Command) {
        'up'     { docker compose up -d }
        'down'   { docker compose down }
        'logs'   { docker compose logs -f }
        'ps'     { docker compose ps }
        'reset'  { docker compose down -v }
        'build'  { & .\mvnw.cmd -q -DskipTests package }
        'test'   { & .\mvnw.cmd test }
        'verify' { & .\mvnw.cmd clean verify }
        'seed'   { Write-Host 'Seed pendiente de implementar en Fase 0' }
        'clean'  { & .\mvnw.cmd clean }
        default {
            Write-Host 'Uso: .\scripts\dev.ps1 <up|down|logs|ps|reset|build|test|verify|seed|clean>'
        }
    }
}
finally {
    Pop-Location
}
