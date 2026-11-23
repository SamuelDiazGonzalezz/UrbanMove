#!/bin/sh
# Lo ejecuta el servicio "terraform" de docker-compose: init + apply sobre LocalStack.
set -e
cd "$(dirname "$0")"
if ! terraform init -input=false -no-color > /tmp/init.log 2>&1; then
  cat /tmp/init.log
  exit 1
fi
if ! terraform apply -auto-approve -input=false -no-color > /tmp/apply.log 2>&1; then
  tail -60 /tmp/apply.log
  exit 1
fi
grep "Apply complete" /tmp/apply.log
