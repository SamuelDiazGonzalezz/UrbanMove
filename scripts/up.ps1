# Atajo opcional: crea .env si falta, levanta todo con Docker Compose y muestra las URLs.
# Equivale a: docker compose up -d --build
$ErrorActionPreference = 'Continue'
Set-Location (Split-Path -Parent $PSScriptRoot)
if (-not (Test-Path .env)) { Copy-Item .env.example .env; Write-Host 'Creado .env a partir de .env.example' }

docker compose up -d --build --wait
if ($LASTEXITCODE -ne 0) { Write-Warning 'Algún servicio no está healthy todavía: revisa docker compose ps' }

Write-Host ''
Write-Host 'UrbanMove listo en http://localhost:8080'
Write-Host '  Usuario      http://localhost:8080/usuario/      (ana / urbanmove)'
Write-Host '  Operaciones  http://localhost:8080/operaciones/  (operador / urbanmove)'
Write-Host '  Dashboard    http://localhost:8080/dashboard/    (gerencia / urbanmove)'
