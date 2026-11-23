#!/bin/sh
# Empaqueta cada Lambda en infra/lambdas/build/<nombre>/ (código + dependencias de requirements.txt).
# Se ejecuta dentro de un contenedor python:3.11-slim (ver scripts/up.*), así no hace falta Python local.
set -e
cd "$(dirname "$0")"
rm -rf build
mkdir -p build
for dir in */; do
  name="${dir%/}"
  [ -f "$name/lambda_function.py" ] || continue
  mkdir -p "build/$name"
  cp "$name"/*.py "build/$name/"
  if [ -f "$name/requirements.txt" ]; then
    pip install --quiet --no-cache-dir --disable-pip-version-check -r "$name/requirements.txt" -t "build/$name"
  fi
  python -c "import shutil,sys; shutil.make_archive(sys.argv[1], 'zip', sys.argv[1])" "build/$name"
  echo "Lambda empaquetada: build/$name.zip"
done
