# DurableFlow

A learning-first distributed workflow orchestration engine built with Java,
Spring Boot, and PostgreSQL.

## Prerequisites

- Java 21
- Docker with Docker Compose

The Maven wrapper is included, so a separate Maven installation is unnecessary.

## Start locally

```bash
docker compose up -d
./mvnw spring-boot:run
```

Spring Security is installed but not configured yet. Until you add the project's
security configuration, Spring Boot protects endpoints with its generated
development credentials printed during startup.

## Verify the project

```bash
./mvnw test
```

The generated context test uses Testcontainers and requires Docker.

## Included foundations

- Spring MVC and Jakarta Validation
- Spring Security and OAuth2 resource-server support
- Spring JDBC and the PostgreSQL driver
- Flyway with PostgreSQL support
- Actuator
- JUnit, Spring Security Test, and Testcontainers for PostgreSQL

## Package structure

```text
com.sidwik.durableflow
├── config       Spring bean and application configuration
├── controller   HTTP request and response handling
├── domain       Business objects, states, and invariants
├── dto          API request and response records
├── exception    Domain and API exceptions
├── mapper       Boundary conversions between domain, API, and persistence
├── repository   Persistence interfaces and PostgreSQL implementations
├── security     Authentication and authorization
├── service      Application use cases and transaction boundaries
└── worker       Distributed activity consumers
```

## Suggested first milestone

Implement an in-memory workflow API before connecting it to PostgreSQL:

```text
POST /api/workflows
GET  /api/workflows/{id}
POST /api/workflows/{id}/cancel
```

Then replace the in-memory repository with a Spring JDBC adapter and a Flyway
migration. Keep domain code independent of Spring and persistence concerns.

Place Flyway migrations in `src/main/resources/db/migration`, beginning with
`V1__create_workflows.sql` when you reach the PostgreSQL milestone.
