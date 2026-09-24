# WGO

Spring Boot 4.1.1, Java 21 backend, with a React + TypeScript frontend in `ui/`.

## Shared environment

Keep all local settings in the repository root `.env`, using `.env.example` as
the single template. Copy it once if `.env` does not exist, then set
`POSTGRES_PASSWORD`. Both files include backend, infrastructure, and UI settings;
do not create component-specific environment files. Quote values containing shell
special characters so the file can also be sourced by the shell.

From the repository root, start the API with:

```sh
(set -a; . ./.env; set +a; cd api && ./mvnw spring-boot:run)
```

Vite loads the root file automatically via `envDir`; run `npm --prefix ui run dev`.
Only `VITE_` settings are exposed to browser code. Keep secrets such as
`ANTHROPIC_API_KEY` without that prefix. Compose uses `--env-file .env`.
See the local development guide for Terraform commands using the same file.
Restart the affected process after editing `.env`; rebuild the UI for changes
to production browser settings. The real `.env` is ignored by Git.

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

## Event categories and map filters

New events can be classified using the official Anthropic Java SDK and Claude's
direct API. Export these variables into the API process (or add them to your
local, ignored `.env` and load it using the setup instructions above):

```sh
export CATEGORIZATION_ENABLED=true
export ANTHROPIC_API_KEY=your-key
# Optional override:
export CATEGORIZATION_MODEL=claude-haiku-4-5-20251001
```

Restart the API after configuring it. Haiku 4.5 is the cheapest active direct-API
Claude model as of September 2026 ($1/million input tokens, $5/million output
tokens; [pricing](https://platform.claude.com/docs/en/about-claude/pricing)).
The key is never sent to the browser.

Categories are **Transport**, **Weather**, **Community**, **Fire**,
**Infrastructure**, and **Other**. Map checkboxes send category and status filters to the backend search endpoint.
Category colors double as a legend. Popups show the category.
`GET /v1/events` includes the uppercase `category` value.

Classification uses the first report when an event is created, with a JSON schema
restricting output to the category enum and application-side validation.
Ambiguous, refused, incomplete or invalid output becomes Other. Categories are
stored in PostgreSQL. Existing events and events created while categorization is
disabled default to Other; enabling Claude does not backfill or reclassify them.
Later matching observations do not change the category. Categories describe
reports, not verified facts.

Categorization is disabled by default. Enabling it without a key fails startup.
API errors roll back matching and use the existing SQS retry/DLQ path. The SDK
uses a 20-second timeout and no internal retries. Once an assignment commits,
duplicate delivery and projection retries reuse its category without another
Claude call. A failed call or a database rollback can incur another call on retry.
The existing database-commit/SQS-publish gap remains unchanged.

Accuracy evaluation is still pending a live key: review representative examples
for every category, ambiguous reports, multilingual reports and reports containing
instructions before expanding the taxonomy. No live Claude calls were made during implementation. No new automated tests
are included.

## Saved event titles and summaries

Start Redis alongside the other local services with Docker Compose, then export
`SUMMARIZATION_ENABLED=true` and `ANTHROPIC_API_KEY` before starting the API.
`SUMMARIZATION_MODEL` defaults to `claude-haiku-4-5-20251001`. This feature works
independently of categorization. Enabling it also enables automatic summaries
for active events, including existing active events without a summary.

**Summarize event** saves the generated title, summary, supporting observation IDs,
model/prompt version, generation time, latency, and token usage in PostgreSQL.
The title replaces the original event title. `POST /v1/events/{id}/summary` returns
the updated event, including `summary`, `generatedSummary`, and `summaryStale`.
Event list/search responses include these same fields, so reopening details
shows saved content without calling Claude. The prompt preserves reported language,
uncertainty and conflicts; source IDs and structured output are validated, but
citations do not prove factual support.

Refresh policy:

- `Event.status` is `ACTIVE` or `INACTIVE`, exposed in API responses and details.
  A new event uses its report timestamp to determine status. The lifecycle worker
  reconciles statuses every minute, even with AI disabled, using `lastObservedAt`
  and `EVENT_ACTIVE_WINDOW` (default `PT24H`). A fresh linked report reactivates an
  inactive event during matching; late historical reports do not extend activity.
  Changing the window also reconciles existing statuses. Inactive does not mean
  verified resolved. Activity and summary freshness are separate states.
- New linked observations increment a revision once; duplicate SQS deliveries do
  not. A worker checks every minute for active events with unsummarized revisions.
- `SUMMARY_REFRESH_INTERVAL` (default `PT15M`) is the minimum delay after a completed
  summary or failed attempt. Incoming reports are combined into the next refresh.
- Identical source revisions always reuse the saved result, even after cache expiry.
  Aging alone does not cause AI calls. Inactive events keep their saved summary;
  a recent observation can reactivate them. The button may create a first summary
  for a historical event but does not repeatedly regenerate an archived summary.
- Button requests follow the same cooldown and return the existing summary while
  an update is pending. A first summary in progress returns 409.

Redis is shared, disposable memory for saved summaries, with versioned keys and
`SUMMARY_CACHE_TTL` (default `PT24H`). Configure `REDIS_HOST`, `REDIS_PORT`, and optional
`REDIS_PASSWORD`. Missing or unavailable Redis falls back to PostgreSQL; cache misses
never generate AI content. Old revision keys expire naturally. Redis has no local
persistent volume because PostgreSQL remains authoritative.

Generation uses a durable five-minute database claim, released after success or
failure, to coordinate app instances and clicks. The 60-second Claude request runs
outside a transaction. Reports arriving during generation remain pending for the
next update. A crash after the provider responds but before database commit can
still cause a later paid retry; exactly-once external API billing is not guaranteed.

Saved updates mark the search projection pending. The projector embeds title plus
summary and indexes both; a separate worker retries pending projections every ten
seconds without calling Claude. Observation processing uses the same projector,
acknowledging SQS only after successful indexing. Per-event database locks serialize
projection writes and event changes. Redis/index failures do not discard a saved
summary. The scheduler processes batches of 20; refresh timings are minimum delays,
not deadlines. Scheduling has three threads so generation does not stop queue polling.

Input over 100,000 serialized characters returns 422 without truncation. Provider or
invalid-output failures return 502 and leave the last saved summary available. Disabled
generation returns saved content if present, otherwise 503. Usage is logged for cost
accounting; no dollar estimate is calculated. Model/prompt changes alone do not regenerate
unchanged sources. Configure positive ISO-8601 durations for refresh and cache settings.

Implementation references: [Claude structured outputs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs),
[Spring Data Redis](https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html),
and [JPA locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html).

## Combined event search

The map always calls `GET /v1/events/search`. Optional parameters:

- `q`: semantic search text; blank or omitted skips embedding and semantic ranking.
- `categories`: comma-separated categories, for example `FIRE,WEATHER`.
- `statuses`: comma-separated statuses, for example `ACTIVE,INACTIVE`.

Omitted category/status parameters include all values; explicitly empty parameters
select none and return an empty list. Unknown enum values return 400. Multiple
values within a filter are ORed; categories, statuses, and text combine with AND.

Example: `/v1/events/search?q=smoke&categories=FIRE&statuses=ACTIVE`.
PostgreSQL selects eligible events using canonical category/status fields. For text
search, their IDs restrict the OpenSearch scoring query before the configured top-K
limit is applied. Without text, all eligible events are returned newest-first.
This avoids reliance on potentially outdated category/status search projections;
semantic results still require indexed embeddings. The eligible-ID list is intended
for the current MVP dataset; a larger dataset will need paginated/indexed filtering.
UI filter changes cancel obsolete requests and clear previous results, including
previously selected markers that may no longer match. No AI call is made for category
or status filtering without text. `GET /v1/events` remains available for existing clients.

## Reporting from the map

Click **Report observation**, then click/tap the map to choose coordinates. Keyboard users
can pan with arrow keys and press Enter or **Use map center**; Escape or **Cancel
selection** exits selection mode. A marker shows the selected point while the form
is open. Enter a description and observation time (defaults to now in the browser's
local timezone). The description is sent as the existing observation `text` field.
Submission creates an observation, and matching
may create an event or attach it to an existing related event.

An event popup also has **Add observation** next to its observations heading. This
uses that event's coordinates and calls `POST /v1/events/{id}/observations` with the
same `{text, location, observedAt}` body as `POST /v1/observations`. A missing target
returns 404; valid submissions return 202 and an observation ID. The intended event
is stored separately as `requested_event_id`; the worker assigns it once through
the normal processing flow, updates event activity/revisions, and reindexes before
acknowledging. Explicitly targeted observations bypass semantic matching.

Report text is limited to 5,200 characters on the backend; the form permits a
5,200-character description. Submission disables duplicate
clicks, preserves form values on failure, and reports queued processing rather than
claiming that an event already exists. Use **Refresh events** after processing;
active search filters may hide the resulting event. The existing database-commit/
SQS-publish gap still applies, so an uncertain submission error may mean a report
was saved even if queue publication failed. No automatic POST retries are made.
