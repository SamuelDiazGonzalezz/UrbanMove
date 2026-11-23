#!/usr/bin/env bash
# Atajo opcional: crea .env si falta, levanta todo con Docker Compose y muestra las URLs.
# Equivale a: docker compose up -d --build
cd "$(dirname "$0")/.."
[ -f .env ] || { cp .env.example .env; echo "Creado .env a partir de .env.example"; }
docker compose up -d --build --wait || echo "Aviso: algún servicio no está healthy todavía (docker compose ps)"
echo
echo "UrbanMove listo en http://localhost:8080"
echo "  Usuario      http://localhost:8080/usuario/      (ana / urbanmove)"
echo "  Operaciones  http://localhost:8080/operaciones/  (operador / urbanmove)"
echo "  Dashboard    http://localhost:8080/dashboard/    (gerencia / urbanmove)"
