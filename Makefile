SHELL := /bin/sh
COMPOSE ?= docker compose

.DEFAULT_GOAL := help

.PHONY: help up down logs ps reset build test verify format lint seed clean

help:
	@echo "UrbanMove - comandos locales"
	@echo "  make up       Levanta la plataforma local"
	@echo "  make down     Para la plataforma"
	@echo "  make reset    Para y borra volúmenes"
	@echo "  make logs     Sigue los logs"
	@echo "  make ps       Estado de contenedores"
	@echo "  make build    Compila el monorepo"
	@echo "  make test     Ejecuta tests"
	@echo "  make verify   Compila, testea y ejecuta checks"
	@echo "  make format   Aplica formato"
	@echo "  make lint     Ejecuta análisis estático"
	@echo "  make seed     Carga datos de demostración"
	@echo "  make clean    Limpia artefactos"

up:
	$(COMPOSE) up -d

down:
	$(COMPOSE) down

reset:
	$(COMPOSE) down -v

logs:
	$(COMPOSE) logs -f

ps:
	$(COMPOSE) ps

build:
	./mvnw -q -DskipTests package

test:
	./mvnw test

verify:
	./mvnw clean verify

format:
	./mvnw spotless:apply

lint:
	./mvnw spotless:check checkstyle:check spotbugs:check

seed:
	@echo "Seed pendiente de implementar en Fase 0"

clean:
	./mvnw clean
