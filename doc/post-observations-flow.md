# POST observation flow

The MVP accepts an observation, persists it as `PENDING`, publishes its ID to SQS,
and processes it asynchronously. PostgreSQL is canonical; OpenSearch is a rebuildable
event projection.

## Systems involved

- **Client** — submits observations.
- **Spring Boot API** — validates `POST /v1/observations` requests.
- **PostgreSQL** — stores observations, events, processing state, and the observation
  to event assignment.
- **SQS** — transports observation IDs through `observation-processing` and retries
  failed deliveries through its redrive policy and DLQ.
- **Observation consumer** — polls SQS, coordinates matching, projection, state
  changes, and message acknowledgement.
- **Embedding provider** — uses the fixed local provider by default and AWS Bedrock
  when configured.
- **OpenSearch** — retrieves event candidates and stores the `events-v1` projection.

## Flow

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant SQS as SQS<br/>observation-processing
    participant Consumer as Observation consumer
    participant Embed as Embedding provider
    participant Search as OpenSearch<br/>events-v1

    Client->>API: POST /v1/observations
    API->>API: Validate text, coordinates, and observedAt
    API->>DB: Insert observation(PENDING)
    DB-->>API: observationId
    API->>SQS: Publish {observationId}
    SQS-->>API: Publish accepted
    API-->>Client: 202 Accepted + observationId

    loop Scheduled polling
        Consumer->>SQS: Receive observation ID
        Consumer->>DB: Mark observation PROCESSING
        Consumer->>DB: Load observation
        Consumer->>Embed: Embed observation text
        Embed-->>Consumer: Embedding vector
        Consumer->>Search: Find nearby, recent, similar events
        Search-->>Consumer: Candidate event IDs
        Consumer->>DB: Lock observation and candidate event

        alt Matching event exists
            Consumer->>DB: Extend event time range
            Consumer->>DB: Assign observation to event
        else No matching event
            Consumer->>DB: Create event
            Consumer->>DB: Assign observation to event
        end

        Consumer->>DB: Commit event and observation changes
        Consumer->>Embed: Embed event title
        Embed-->>Consumer: Embedding vector
        Consumer->>Search: Upsert event by PostgreSQL event ID
        Search-->>Consumer: Projection indexed
        Consumer->>DB: Mark observation PROCESSED
        Consumer->>SQS: Delete message (acknowledge)
    end

    alt Processing or indexing fails
        Consumer-->>SQS: Leave message unacknowledged
        SQS-->>Consumer: Retry delivery
        SQS-->>SQS: Move to DLQ after max receives
    end
```

The database commit and SQS publish are separate operations in v0.1. A successful
database commit followed by a publish failure can leave a `PENDING` observation without
a queue message; the MVP accepts this gap and does not add an outbox.
