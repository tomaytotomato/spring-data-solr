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

1. Create a new Railway project.
2. Add two services — **Solr** and **App** — each pointing at this repo.

**Solr service**
- Source: Docker image `ghcr.io/tomaytotomato/spring-data-solr-solr:latest`
- Start command: `solr-precreate books`
- No environment variables needed

**App service**
- Source: Docker image `ghcr.io/tomaytotomato/spring-data-solr-sample:latest`
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
| `ghcr.io/tomaytotomato/spring-data-solr-sample:latest` | Spring Boot app (built with buildpacks) |
| `ghcr.io/tomaytotomato/spring-data-solr-solr:latest` | Solr 10 with `managed-schema.xml` baked in |

## API endpoints

All endpoints are under `/api/books`.

| Method | Path | Params | Description |
|---|---|---|---|
| `GET` | `/api/books` | — | List all books |
| `GET` | `/api/books/{id}` | — | Find book by ID |
| `POST` | `/api/books` | body: Book JSON | Save a book |
| `DELETE` | `/api/books/{id}` | — | Delete a book |
| `GET` | `/api/books/search` | `q` | Full-text title search |
| `GET` | `/api/books/starting-with` | `prefix` | Title prefix search |
| `GET` | `/api/books/by-author` | `author` | Find by author |
| `GET` | `/api/books/by-author-after` | `author`, `year` | Author + year filter (paginated) |
| `GET` | `/api/books/in-stock` | — | In-stock books only |
| `GET` | `/api/books/top-rated` | `minRating` (default 4.0) | Books above rating threshold |
| `GET` | `/api/books/price-range` | `low`, `high` | Price range filter |
| `GET` | `/api/books/by-year-range` | `from`, `to` | Year range filter |
| `GET` | `/api/books/cheap` | `maxPrice` (default 15) | Books below price threshold |
| `GET` | `/api/books/highlight` | `q` | Search with highlighted snippets |
| `GET` | `/api/books/facets` | `q` (default `*`) | Search with category/author facets |
| `GET` | `/api/books/cursor` | `cursorMark`, `pageSize` | Cursor-based deep pagination |
| `GET` | `/api/books/nearby` | `lat`, `lon`, `radiusKm` | Geo search by radius |
| `GET` | `/api/books/within` | `lat`, `lon`, `radiusKm` | Geo search within bounds |
| `PATCH` | `/api/books/{id}/price` | `price` | Partial update — price only |
| `POST` | `/api/books/{id}/category` | `category` | Partial update — add category |
| `GET` | `/api/books/stats` | — | Total/in-stock/out-of-stock counts |
| `GET` | `/actuator/health` | — | Health check (includes Solr) |
