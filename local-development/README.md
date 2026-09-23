# Local development and debugging

Run these commands from the repository root. The root `.env` file supplies the
API, UI, Compose, and Terraform settings. The root `.env.example` is the only
template; keep local values in `.env` and do not create per-component env files.

For map testing, see [the world demo data](demo/README.md): 80 fictional events
with a mix of city clusters and isolated locations, submitted through the API.

## Start infrastructure

```sh
[ -f .env ] || cp .env.example .env
# Set POSTGRES_PASSWORD in .env
docker compose -f local-development/compose.yaml --env-file .env config --quiet
docker compose -f local-development/compose.yaml --env-file .env up -d --wait --wait-timeout 240
docker compose -f local-development/compose.yaml --env-file .env ps
```

The stack exposes PostgreSQL on `localhost:5432`, Adminer on
`http://localhost:8081`, LocalStack on `http://localhost:4566`, OpenSearch on
`http://localhost:9200`, and OpenSearch Dashboards on `http://localhost:5601`.
Override ports in the root `.env` as needed.
The PostgreSQL password is used when the database is first initialized; editing
it later does not change the password in an existing database volume.

PostgreSQL and OpenSearch data are stored in `local-development/data/`, which is
ignored by Git. LocalStack state is ephemeral. The `localstack-init` one-shot service
creates `observation-processing` after LocalStack reports healthy; Terraform adds the
DLQ and redrive policy.

To stop or inspect the stack:

```sh
docker compose -f local-development/compose.yaml --env-file .env stop
docker compose -f local-development/compose.yaml --env-file .env down
docker compose -f local-development/compose.yaml --env-file .env logs --tail=100
```

## API

Export the root `.env` before starting Spring Boot because Spring Boot does not load
Docker Compose's `.env` file automatically:

```sh
(set -a; . ./.env; set +a; cd api && ./mvnw spring-boot:run)
```

Open Swagger UI at [http://localhost:8080/](http://localhost:8080/). The direct UI
path is [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html),
and the generated specification is available at
[http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs).

Submit an observation:

```sh
curl -i -X POST http://localhost:8080/v1/observations \
  -H 'Content-Type: application/json' \
  -d '{"text":"Huge fire next to Rotterdam Centraal","location":{"latitude":51.9244,"longitude":4.4697},"observedAt":"2026-09-20T18:30:00Z"}'
```

List canonical events:

```sh
curl -s http://localhost:8080/v1/events | jq
```

The API persists an observation as `PENDING`, publishes its ID to SQS, and the
scheduled consumer processes it asynchronously. Check the application logs for
`PROCESSED`, event assignment, projection indexing, and message acknowledgment.

## PostgreSQL and Adminer

Inspect PostgreSQL from the command line:

```sh
docker compose -f local-development/compose.yaml --env-file .env exec -T postgres \
  psql -U wgo -d wgo -c 'SELECT version();'
docker compose -f local-development/compose.yaml --env-file .env exec -T postgres \
  psql -U wgo -d wgo -c 'SELECT id, processing_status, event_id FROM wgo.observations ORDER BY created_at DESC;'
docker compose -f local-development/compose.yaml --env-file .env exec -T postgres \
  psql -U wgo -d wgo -c 'SELECT id, title, latitude, longitude, started_at, last_observed_at FROM wgo.events ORDER BY started_at DESC;'
```

Or open [Adminer](http://localhost:8081) and sign in with:

| Field | Value |
| --- | --- |
| System | PostgreSQL |
| Server | `postgres` |
| Username | `wgo` |
| Password | `POSTGRES_PASSWORD` from `.env` |
| Database | `wgo` |

## SQS and Terraform

The queue URL and messages can be inspected through LocalStack:

```sh
docker compose -f local-development/compose.yaml --env-file .env exec -T localstack \
  awslocal sqs list-queues --region us-east-1
docker compose -f local-development/compose.yaml --env-file .env exec -T localstack \
  awslocal sqs get-queue-url --queue-name observation-processing --region us-east-1
docker compose -f local-development/compose.yaml --env-file .env exec -T localstack \
  awslocal sqs receive-message --queue-url "$$(docker compose -f local-development/compose.yaml --env-file .env exec -T localstack awslocal sqs get-queue-url --queue-name observation-processing --region us-east-1 --query QueueUrl --output text)" --region us-east-1
```

Apply the Terraform queue and redrive policy configuration after LocalStack starts:

```sh
(
  set -a
  . ./.env
  set +a
  terraform -chdir=infra/terraform init &&
  terraform -chdir=infra/terraform plan -out=local.tfplan \
    -var="localstack_endpoint=${SQS_ENDPOINT:-http://localhost:${LOCALSTACK_PORT:-4566}}" \
    -var="aws_region=${AWS_REGION:-us-east-1}" \
    -var="observation_queue_name=${OBSERVATION_QUEUE_NAME:-observation-processing}" &&
  terraform -chdir=infra/terraform apply local.tfplan
)
```

## OpenSearch and Dashboards

Check cluster health and the `events-v1` index:

```sh
curl --fail 'http://localhost:9200/_cluster/health?pretty'
curl 'http://localhost:9200/_cat/indices?v'
curl 'http://localhost:9200/events-v1/_count?pretty'
curl 'http://localhost:9200/events-v1/_mapping?pretty'
curl 'http://localhost:9200/events-v1/_search?pretty'
```

Open [OpenSearch Dashboards](http://localhost:5601), choose **Explore on my own**,
then create an index pattern under **Stack Management → Index Patterns**:

1. Enter `events-v1*`.
2. Select `startedAt` as the time field.
3. Open **Discover** and select `events-v1*`.

The event document ID is the PostgreSQL event ID. PostgreSQL remains the canonical
source; the OpenSearch index can be rebuilt.

## E2E test

The single E2E test starts disposable PostgreSQL, LocalStack, and OpenSearch
containers with Testcontainers. No Compose services are needed for it:

```sh
cd api
./mvnw -Dit.test=HappyPathFullE2EIT verify
```

## Reset local data

To remove PostgreSQL and OpenSearch data, stop Compose and delete the ignored data
directory:

```sh
docker compose -f local-development/compose.yaml --env-file .env down
rm -rf local-development/data
```
