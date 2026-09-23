# GET events flow

The API reads the canonical event records from PostgreSQL and includes only the
observations assigned to those events. This request is synchronous and does not
poll SQS or query OpenSearch.

## Systems involved

- **Client** — requests the current event list.
- **Spring Boot API** — exposes `GET /v1/events` and maps entities to response DTOs.
- **PostgreSQL** — supplies events and their assigned observations in canonical form.

## Flow

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant API as Spring Boot API
    participant DB as PostgreSQL

    Client->>API: GET /v1/events
    API->>DB: Read events ordered by startedAt
    DB-->>API: Event rows
    API->>DB: Read observations assigned to returned event IDs
    DB-->>API: Observation rows
    API->>API: Map entities to EventResponse and ObservationResponse
    API-->>Client: 200 OK + event list with observations
```

OpenSearch is not part of this read path. It is used during asynchronous
observation processing to find candidate matches; PostgreSQL remains the source
of truth for the response.
