# ECOM platform — convenience targets.
# Usage: make help

SHELL := /bin/bash

COMPOSE_BASE := docker compose -f docker-compose.yml
LOCAL := $(COMPOSE_BASE) -f docker-compose.local.yml --env-file .env.local
DEV   := $(COMPOSE_BASE) -f docker-compose.dev.yml   --env-file .env.dev
PROD  := $(COMPOSE_BASE) -f docker-compose.prod.yml  --env-file .env.prod

NET     := ecom_ecom-net
FLYWAY  := flyway/flyway:10
PWDDIR  := $(shell pwd)

.DEFAULT_GOAL := help

## ── Help ─────────────────────────────────────────────
help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | \
		awk 'BEGIN{FS=":.*?## "}{printf "\033[36m%-22s\033[0m %s\n", $$1, $$2}'

## ── Local (infra-only by default) ────────────────────
up-local: env-local ## Start 4 Postgres + pgAdmin (no apps; run apps from IDE)
	$(LOCAL) up -d postgres-user postgres-catalog postgres-order postgres-payment pgadmin

up-local-full: env-local ## Start everything incl. all 4 services + gateway in containers
	$(LOCAL) --profile full up -d --build

down-local: ## Stop local stack
	$(LOCAL) down

logs-local: ## Tail local logs
	$(LOCAL) logs -f

## ── Dev environment ──────────────────────────────────
up-dev: env-dev ## Build + start full dev stack (containers)
	$(DEV) up -d --build

down-dev: ## Stop dev stack
	$(DEV) down

logs-dev: ## Tail dev logs
	$(DEV) logs -f

## ── Prod environment ─────────────────────────────────
up-prod: env-prod ## Start prod stack (images must be prebuilt or pulled)
	$(PROD) up -d

down-prod: ## Stop prod stack
	$(PROD) down

## ── Build / Test ─────────────────────────────────────
MVN := $(shell [ -x ./mvnw ] && echo ./mvnw || echo mvn)

build: ## Build all services
	$(MVN) -B -DskipTests package

test: ## Run tests for all services
	$(MVN) -B test

build-user:    ## Build only user-service
	$(MVN) -B -pl services/user-service -am -DskipTests package

build-catalog: ## Build only catalog-service
	$(MVN) -B -pl services/catalog-service -am -DskipTests package

build-order:   ## Build only order-service
	$(MVN) -B -pl services/order-service -am -DskipTests package

build-payment: ## Build only payment-service
	$(MVN) -B -pl services/payment-service -am -DskipTests package

## ── Migrations (apply without starting Spring apps) ──
migrate-user: ## Apply user-service migrations (incl. seed)
	docker run --rm --network $(NET) \
		-v "$(PWDDIR)/services/user-service/src/main/resources/db/migration:/flyway/sql/migration" \
		-v "$(PWDDIR)/services/user-service/src/main/resources/db/seed:/flyway/sql/seed" \
		$(FLYWAY) \
		-url=jdbc:postgresql://postgres-user:5432/user_db \
		-user=user_svc -password=user_svc \
		-locations=filesystem:/flyway/sql/migration,filesystem:/flyway/sql/seed \
		-baselineOnMigrate=true migrate

migrate-catalog: ## Apply catalog-service migrations (incl. seed)
	docker run --rm --network $(NET) \
		-v "$(PWDDIR)/services/catalog-service/src/main/resources/db/migration:/flyway/sql/migration" \
		-v "$(PWDDIR)/services/catalog-service/src/main/resources/db/seed:/flyway/sql/seed" \
		$(FLYWAY) \
		-url=jdbc:postgresql://postgres-catalog:5432/catalog_db \
		-user=catalog -password=catalog \
		-locations=filesystem:/flyway/sql/migration,filesystem:/flyway/sql/seed \
		-baselineOnMigrate=true migrate

migrate-order: ## Apply order-service migrations
	docker run --rm --network $(NET) \
		-v "$(PWDDIR)/services/order-service/src/main/resources/db/migration:/flyway/sql/migration" \
		$(FLYWAY) \
		-url=jdbc:postgresql://postgres-order:5432/order_db \
		-user=orderuser -password=orderpass \
		-locations=filesystem:/flyway/sql/migration \
		-baselineOnMigrate=true migrate

migrate-payment: ## Apply payment-service migrations
	docker run --rm --network $(NET) \
		-v "$(PWDDIR)/services/payment-service/src/main/resources/db/migration:/flyway/sql/migration" \
		$(FLYWAY) \
		-url=jdbc:postgresql://postgres-payment:5432/payment_db \
		-user=paymentuser -password=paymentpass \
		-locations=filesystem:/flyway/sql/migration \
		-baselineOnMigrate=true migrate

migrate-all: migrate-user migrate-catalog migrate-order migrate-payment ## Apply migrations for all services

## ── DB shells ────────────────────────────────────────
psql-user: ## psql into user DB
	docker exec -it ecom-postgres-user psql -U user_svc -d user_db

psql-catalog: ## psql into catalog DB
	docker exec -it ecom-postgres-catalog psql -U catalog -d catalog_db

psql-order: ## psql into order DB
	docker exec -it ecom-postgres-order psql -U orderuser -d order_db

psql-payment: ## psql into payment DB
	docker exec -it ecom-postgres-payment psql -U paymentuser -d payment_db

tables: ## List tables in all 4 databases
	@echo "── User ──";     docker exec ecom-postgres-user     psql -U user_svc    -d user_db    -c "\dt" || true
	@echo "── Catalog ──";  docker exec ecom-postgres-catalog psql -U catalog     -d catalog_db -c "\dt" || true
	@echo "── Order ──";    docker exec ecom-postgres-order   psql -U orderuser   -d order_db   -c "\dt" || true
	@echo "── Payment ──";  docker exec ecom-postgres-payment psql -U paymentuser -d payment_db -c "\dt" || true

db-reset: ## DROP & recreate ALL DB volumes (DESTRUCTIVE)
	$(LOCAL) down -v
	$(LOCAL) up -d postgres-user postgres-catalog postgres-order postgres-payment pgadmin

## ── Env scaffolding ──────────────────────────────────
env-local: .env.local
env-dev:   .env.dev
env-prod:  .env.prod

.env.local:
	@cp .env.local.example .env.local && echo "Created .env.local from template"

.env.dev:
	@cp .env.dev.example .env.dev && echo "Created .env.dev from template"

.env.prod:
	@cp .env.prod.example .env.prod && echo "Created .env.prod — EDIT IT before running prod"

.PHONY: help up-local up-local-full down-local logs-local up-dev down-dev logs-dev up-prod down-prod \
        build test build-user build-catalog build-order build-payment \
        migrate-user migrate-catalog migrate-order migrate-payment migrate-all \
        psql-user psql-catalog psql-order psql-payment tables db-reset \
        env-local env-dev env-prod
