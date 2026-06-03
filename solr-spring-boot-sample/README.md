# spring-data-solr sample app

A bookstore REST API demonstrating the `spring-data-solr` starter. Loads ~7,000 curated books into
Apache Solr on startup and exposes endpoints for search, faceting, highlighting, and cursor-based
pagination.

## Running locally

Requires Docker (for Solr) and JDK 21+.

```bash
# from the repo root
./mvnw spring-boot:run -pl solr-spring-boot-sample -Dspring-boot.run.profiles=local
```

The `local` profile activates Spring Boot's Docker Compose integration, which starts a Solr 10
container automatically and stops it when the app shuts down. Books are indexed on every startup.

The API is available at `http://localhost:8080`. Actuator health at `http://localhost:8080/actuator/health`.

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `SPRING_SOLR_STANDALONE_HOST` | `http://localhost:8983/solr` | Solr base URL |
| `SPRING_PROFILES_ACTIVE` | _(none)_ | Set to `local` to enable Docker Compose auto-start |

## Deploying to Railway

### One-click deploy

[![Deploy on Railway](https://railway.com/button.svg)](https://railway.com/deploy/rhCU8p?referralCode=_GAcKG&utm_medium=integration&utm_source=template&utm_campaign=generic)

This template provisions both the Solr and App services and wires `SPRING_SOLR_STANDALONE_HOST`
automatically.

### Manual setup

1. Create a new Railway project.
2. Add two services — **Solr** and **App** — each pointing at this repo.

**Solr service**
- Source: Docker image `ghcr.io/tomaytotomato/spring-data-solr-solr10:latest`
- Start command: `solr-precreate books`
- No environment variables needed

**App service**
- Source: Docker image `ghcr.io/tomaytotomato/spring-data-solr-bookstore:latest`
- Environment variables:
  ```
  SPRING_SOLR_STANDALONE_HOST=http://<solr-service-internal-host>:8983/solr
  ```
  Replace `<solr-service-internal-host>` with the Railway internal hostname for your Solr service
  (visible in the Railway dashboard under the service's networking tab).

## Docker images

Both images are published to GHCR on every push to `master`:

| Image | Contents |
|---|---|
| `ghcr.io/tomaytotomato/spring-data-solr-bookstore:latest` | Spring Boot app (built with buildpacks) |
| `ghcr.io/tomaytotomato/spring-data-solr-solr10:latest` | Solr 10 with `managed-schema.xml` baked in |

## API reference

Interactive API docs (Swagger UI) are available at `/docs` once the app is running.
The raw OpenAPI spec is at `/api-docs`.

```
http://localhost:8080/docs
```
