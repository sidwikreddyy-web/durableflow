# DurableFlow

DurableFlow is a backend-only order workflow engine built to teach production Spring Boot and distributed-systems concepts. A workflow reserves inventory, charges payment, and creates a shipment. Work is persisted before execution, multiple workers can claim tasks safely, transient failures retry with backoff, and permanent downstream failures trigger compensating actions.

## What is implemented

- Java 21 and Spring Boot 4
- Registration, login, BCrypt password hashing, JWT access tokens, rotating refresh-token cookies, logout, and user isolation
- PostgreSQL persistence through Spring JDBC
- Flyway-managed schema and indexes
- Transactional workflow state transitions with optimistic locking
- Durable database task queue using leases and `FOR UPDATE SKIP LOCKED`
- Retry scheduling with exponential backoff
- Order saga compensation: refund payment and release inventory
- Append-only workflow event history
- User cancellation and expired-worker lease recovery
- Virtual threads, Actuator health checks, CORS configuration, Docker Compose, and a production Dockerfile

## Architecture

```text
HTTP client
    |
Spring MVC controllers
    |
Application services + domain state machine
    |
Spring JDBC repositories
    |
PostgreSQL
  ├── users and refresh sessions
  ├── workflow state + optimistic version
  ├── append-only workflow events
  └── durable tasks + worker leases
                ^
                |
       scheduled worker pool
```

The API and worker can run in the same process for the MVP. PostgreSQL is the coordination point, so additional application instances can claim different tasks without a separate broker. The claim query locks one available row, skips rows locked by other workers, and records an ownership lease. If a process dies, another worker returns the expired task to the pending queue.

## Run locally

Requirements: Java 21 and Docker Desktop. The Maven wrapper is included.

```bash
docker compose up -d
./mvnw spring-boot:run
```

The API starts at `http://localhost:8080`. Flyway creates the schema automatically. Check it with:

```bash
curl http://localhost:8080/actuator/health
```

To stop the database:

```bash
docker compose down
```

Add `-v` only when you intentionally want to delete the local database volume.

## Use the API

Register and save the refresh-token cookie:

```bash
curl -c cookies.txt \
  -H 'Content-Type: application/json' \
  -d '{"email":"sidwik@example.com","password":"change-me-now"}' \
  http://localhost:8080/api/auth/register
```

Copy `accessToken` from the response and send it as a bearer token:

```bash
curl -H 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"name":"order-1001"}' \
  http://localhost:8080/api/workflows
```

Then inspect the workflow and its durable event history:

```bash
curl -H 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  http://localhost:8080/api/workflows/WORKFLOW_ID

curl -H 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  http://localhost:8080/api/workflows/WORKFLOW_ID/events
```

Refresh and logout use the HTTP-only cookie:

```bash
curl -b cookies.txt -c cookies.txt -X POST http://localhost:8080/api/auth/refresh
curl -b cookies.txt -X POST http://localhost:8080/api/auth/logout
```

## Endpoints

| Method | Path | Authentication | Purpose |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Create an account and session |
| `POST` | `/api/auth/login` | Public | Create a session |
| `POST` | `/api/auth/refresh` | Refresh cookie | Rotate the refresh token |
| `POST` | `/api/auth/logout` | Refresh cookie | Revoke the session |
| `GET` | `/api/auth/me` | Bearer token | Read the current user |
| `POST` | `/api/workflows` | Bearer token | Start an order workflow |
| `GET` | `/api/workflows` | Bearer token | List the current user's workflows |
| `GET` | `/api/workflows/{id}` | Bearer token | Read current workflow state |
| `POST` | `/api/workflows/{id}/cancel` | Bearer token | Cancel active work |
| `GET` | `/api/workflows/{id}/events` | Bearer token | Read its event timeline |

## Demo failure scenarios

The local activity adapter uses the workflow name to make distributed failure paths easy to observe:

| Name contains | Behaviour | Final state |
|---|---|---|
| `retry-payment` | Payment fails once, then succeeds | `COMPLETED` |
| `fail-inventory` | Inventory exhausts all attempts | `FAILED` |
| `fail-shipping` | Shipping exhausts attempts; payment and inventory are compensated | `COMPENSATED` |

These switches belong to the demo adapter. Replacing `WorkflowTaskProcessor.executeDemoActivity` with real HTTP or message-based integrations does not change the durable orchestration layer.

## Configuration

Copy `.env.example` values into your environment or deployment platform. Local defaults already match `compose.yml`.

| Variable | Meaning |
|---|---|
| `DATABASE_URL` | JDBC PostgreSQL URL |
| `DATABASE_USERNAME` | Database username |
| `DATABASE_PASSWORD` | Database password |
| `JWT_SECRET` | HMAC key, at least 32 bytes; required to be unique in production |
| `ALLOWED_ORIGINS` | Comma-separated browser origins |
| `SECURE_COOKIES` | Set `true` behind HTTPS |
| `WORKER_ENABLED` | Enable task polling in this process |
| `PORT` | HTTP port |

## Production container

```bash
docker build -t durableflow .
docker run --rm -p 8080:8080 --env-file .env durableflow
```

For a split deployment, run the same image with `WORKER_ENABLED=false` for API-only instances and `WORKER_ENABLED=true` for worker instances. A worker deployment must remain active long enough to poll; a request-only service that scales to zero will not process background tasks while idle.

## Package layout

```text
com.sidwik.durableflow
├── config       Application beans
├── controller   HTTP boundaries
├── domain       State machine, entities, and enums
├── dto          Validated request and response records
├── exception    API error mapping
├── repository   Persistence ports and JDBC adapters
├── security     Spring Security and token creation
├── service      Transactions and application use cases
└── worker       Durable task polling
```

No hand-written tests were added in this MVP. Build without running the generated test with `./mvnw -DskipTests package`.
