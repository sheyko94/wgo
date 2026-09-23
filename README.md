# WGO

Spring Boot 4.1.1, Java 21 backend, with a React + TypeScript frontend in `ui/`.

## Frontend

See [ui/README.md](ui/README.md) for setup and the incremental UI plan.
The frontend uses Vite and plain CSS, with MapLibre GL JS and an OpenFreeMap background.

```sh
cd ui
npm ci
npm run dev
```

Open http://localhost:5173. The initial page runs independently of the backend.
During development, `/v1` requests are proxied to Spring Boot on port 8080.

## Local development

See [local-development/README.md](local-development/README.md) for the complete local
startup and debugging guide, including API calls, PostgreSQL/Adminer, SQS/Terraform,
OpenSearch/Dashboards, and the E2E test.

See [doc/data-models.md](doc/data-models.md) for the PostgreSQL, SQS, and OpenSearch
models and how their identifiers connect.

The request flows are documented separately: [POST observations](doc/post-observations-flow.md),
[POST observations (simple)](doc/post-observations-flow-simple.md), [GET events](doc/get-events-flow.md),
and [GET events (simple)](doc/get-events-flow-simple.md).

## Application

Set `JAVA_HOME` to a JDK 21 installation. The application startup command and local
environment setup are documented in [local-development/README.md](local-development/README.md).

By default, the application connects to database `wgo` as user `wgo` on
`localhost:${POSTGRES_PORT:-5432}`, using `POSTGRES_PASSWORD`. Set `DATABASE_URL`
(a PostgreSQL JDBC URL) and `DATABASE_USERNAME` to override the connection.
Credentials stay outside source control.

Flyway applies migrations on startup to the dedicated `wgo` schema. Flyway alone
owns application schema changes.
Primary keys provide the initial database indexes.

`observations` and `events` use UUID keys and scalar latitude/longitude columns.
Database checks and Java validation reject non-finite or out-of-range coordinates.
Timestamps are stored as `TIMESTAMP WITH TIME ZONE`
and mapped to Java `Instant`, with PostgreSQL microsecond precision.

Observations start as `PENDING` by database default and may be `PROCESSING`,
`PROCESSED`, or `FAILED`. A nullable foreign key links each observation to at most
one event. `PROCESSED` requires both an event and a completion timestamp; other
statuses may retain an assignment for projection retries but have no completion
timestamp. Referenced events cannot be deleted, and event time ranges must be
ordered.

Repositories save and read canonical records by ID. Embeddings are
generated for the OpenSearch projection; PostgreSQL does not store vectors.
`EmbeddingService` checks provider vector dimensions and finite numeric values
before returning them to its caller.
`EventIndex` stores event documents under their PostgreSQL event IDs and
retrieves candidates using configurable geographic distance, time window, top-K,
and minimum cosine similarity. Transactional assignment and event time-range
updates are implemented by `ObservationEventMatcher`: it locks the observation,
creates or updates one event, assigns the observation once, and safely ignores
duplicate processing. The scheduled consumer marks observations `PROCESSING`, runs matching,
indexes the event projection, marks the observation `PROCESSED`, and deletes the
message only after all those operations succeed. Failed messages remain available
for retry.

## Build

With Docker running for the application dependencies:

```sh
cd api
./mvnw -Dmaven.test.skip=true package
```

The project keeps one end-to-end integration test. It reads JSON observation
requests, posts them through the API, drives the consumer, and calls
`GET /v1/events`:

```sh
./mvnw -Dit.test=HappyPathFullE2EIT verify
```

The integration test starts disposable PostgreSQL, LocalStack (SQS), and OpenSearch
containers through Testcontainers. No Compose services or pre-existing local containers
are required. It uses the real `SqsClient` and OpenSearch projection.

## Code quality

Spotless formats Java sources with the pinned Palantir Java Format version. Formatting is
checked automatically during Maven `validate`, so `verify` fails if a change is not
formatted. Apply formatting after editing with:

```sh
./mvnw spotless:apply
```

## Embeddings

`EmbeddingService` is the embedding component. Matching code calls
`EmbeddingService.embed(text)` and reads `dimensions()` for the configured vector
size. Embeddings are stored in the OpenSearch event projection; PostgreSQL
stores only canonical observations and events plus their IDs.

The default provider is `fixed`, which returns a deterministic vector of the
configured dimension and needs no external service. Set `EMBEDDING_PROVIDER=bedrock`
to use AWS Bedrock Titan Text Embeddings V2. Configure `AWS_REGION`, AWS
credentials, `BEDROCK_ENDPOINT` (optional), and `BEDROCK_INFERENCE_MODEL`.
LocalStack emulates SQS only, so Bedrock calls use AWS or another
compatible endpoint; the fixed provider is the local default.

Use the same model and dimensions for observations and events. Changing the model
requires regenerating existing vectors; changing dimensions requires rebuilding
the OpenSearch index. `POST /v1/observations` persists and queues reports,
while the consumer generates embeddings and updates the event projection. The
fixed provider avoids external calls during local development.

## API usage

See [local-development/README.md](local-development/README.md) for Swagger UI,
`curl` examples, API responses, and local debugging commands.
