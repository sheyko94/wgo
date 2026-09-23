# Repository Guidelines

## Purpose & Scope

Source: `world_events_mvp_spec.pdf` (provided from `/Users/ivangonzalez/Downloads/`). This repository contains the completed first MVP iteration for clustering reports using text, coordinates, and time.

The MVP scope excludes frontend/maps, authentication, Kafka, moderation/reputation, media, verification, news ingestion, WebSockets, complex summaries, and production infrastructure.

## Architecture & Invariants

Use one Spring Boot application. PostgreSQL owns canonical observations and events; SQS distributes work; OpenSearch stores a rebuildable `events-v1` projection. Application code decides matches. Each observation belongs to at most one event through nullable `event_id`.

Processing must be idempotent, including projection retries: duplicate delivery must not duplicate assignments or mutate event time ranges twice. Acknowledge only after successful processing and indexing; failures retry, then reach the DLQ. Accept and document the database-commit/SQS-publish gap without adding an outbox in v0.1.

## Structure & Style

The scaffold declares Spring Boot 4.1.1 and Java 21, matching the spec's Java target. Spring Boot 4.1.1 supports Java 17 through 26 (https://docs.spring.io/spring-boot/system-requirements.html). The API source lives in `api/src/main/java/com/example/wgo/`; add `observation`, `event`, `matching`, `search`, `messaging`, and configuration packages as needed. Resources belong in `api/src/main/resources/`, with Flyway migrations in `api/src/main/resources/db/migration/`.

Use tabs in Java/XML, two-space YAML indentation, `UpperCamelCase` classes, and `lowerCamelCase` methods. No formatter is configured. Keep secrets external and matching parameters configurable.

Use Terraform in `infra/terraform` for local AWS resources; Docker Compose runs PostgreSQL, LocalStack, and OpenSearch. Do not provision infrastructure with Python. Store coordinates as scalar latitude/longitude values. Primary keys provide the initial database indexes.

## Commands & Contribution Checks

- See `README.md` for local infrastructure setup and `.env` configuration.
- `docker compose -f local-development/compose.yaml --env-file .env up -d --wait --wait-timeout 240`: start PostgreSQL, LocalStack, and OpenSearch and verify readiness.
- `docker compose -f local-development/compose.yaml down`: stop infrastructure, retaining data under `local-development/data/`.
- `terraform -chdir=infra/terraform init`: install the pinned infrastructure provider.
- `terraform -chdir=infra/terraform plan -out=local.tfplan` then `terraform -chdir=infra/terraform apply local.tfplan`: provision local SQS queues and redrive policy after Compose starts.
- `cd api && ./mvnw spring-boot:run`: run locally with PostgreSQL running and database credentials exported (see `README.md`).
- `cd api && ./mvnw -Dmaven.test.skip=true package`: compile and package the API.
- `cd api && ./mvnw -Dit.test=HappyPathFullE2EIT verify`: run the single end-to-end integration test.

Use the JDK declared in `api/pom.xml`. Prefer focused changes and imperative commits. PRs should explain behavior, link relevant issues, and record validation results and limitations.
