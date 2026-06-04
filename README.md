![title.png](assets/title.png)

# Spring Data Solr - Lazarus

[![CI](https://github.com/tomaytotomato/spring-data-solr/actions/workflows/build.yml/badge.svg)](https://github.com/tomaytotomato/spring-data-solr/actions/workflows/build.yml)
![Java](https://img.shields.io/badge/Java-21%2B-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen)
![Solr](https://img.shields.io/badge/Solr-9%20%7C%2010-blue)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)
[![Live Demo](https://img.shields.io/badge/Live%20Demo-Railway-7B2CF2)](https://spring-data-solr-sample-book-production.up.railway.app)

Sometimes you wonder if a project is really dead or if it's just taking a break in the attic.

Like Lazarus, the [Spring Data Solr](https://github.com/spring-attic/spring-data-solr) project has been resurrected from the cold dark bit void.

This revival was driven by a few motivations:

- **Activity**; Solr remains a highly active and popular search database. Solr is also a technology I enjoyed using.
- **LLM maintenance and development**; creating a library is "easy", maintaining and growing it is the hard part.
- **Specified requirements over instinct**: rather than vibe code the library, lets specify what we want and then verify it with tests.

## Features

| Feature                      | Description            | Key Capabilities                                                                                       |
|:-----------------------------|:-----------------------|:-------------------------------------------------------------------------------------------------------|
| **Auto-configuration**       | Drop-in Bean Support   | Automatically configures `SolrClient` (Standalone or SolrCloud) and `SolrTemplate`.                    |
| **Spring Data Repositories** | `SolrRepository`       | Full **CRUD** support, including built-in pagination and sorting.                                      |
| **Derived Query Methods**    | Keyword-based queries  | Supports **18 keywords** (e.g., `Is`, `Containing`, `Between`, `GreaterThan`, `In`, `IsNull`, `True`). |
| **@Field Name Mapping**      | Annotation respect     | Derived queries respect SolrJ `@Field` annotations; fixes original mapping pain points.                |
| **@Query Annotation**        | Manual Query Strings   | Supports raw Solr queries with `?0`, `?1` parameter substitution.                                      |
| **Highlighting**             | `HighlightPage`        | Configurable pre/post tags, snippets, and fragment size.                                               |
| **Faceting**                 | `FacetPage`            | Includes field facets, query facets, min count, and limit control.                                     |
| **Deep Paging**              | Cursor-based iteration | `CursorResult` wraps Solr's `cursorMark` for efficient deep iteration.                                 |
| **Partial Updates**          | Atomic Operations      | Supports `set`, `add`, and `increment` operations via `PartialUpdate`.                                 |
| **Health Indicator**         | Spring Boot Actuator   | Integration that pings collections or falls back to admin info.                                        |
| **Criteria API**             | Fluent Query Builder   | Programmatic builder: `Criteria.where("title").contains("spring").and("price").greaterThan(10)`.       |


## Why?

![ytho.png](assets/ytho.png)

Projects often go stale or die because of developer burnout, lack of time/resources or changing
priorities from the community.

What if we could prevent this from happening by using LLMs to take on the maintenance burden?

The gaps in API drift would be managed, old dependencies and security issues would be resolved, and
the
project would stay alive and useful for the community.

The original Spring Data Solr
was [discontinued in April 2020](https://spring.io/blog/2020/04/07/spring-data-for-apache-solr-discontinued/)
and [archived in September 2023](https://github.com/spring-attic/spring-data-solr).

After this the world moved on; Solr got updated, SolrJ was updated and Spring Boot went to version 3.

However there was no pathway for anyone to continue to use Spring boot with Solr in a project.

Lazarus is a clean re-implementation with a baseline in the latest versions of all these libraries.

This is not a fork of the original library.

See [LIMITATIONS.md](docs/LIMITATIONS.md) for a detailed comparison of the original spring-data-solr
project.

## Module Structure

```
solr-spring-boot-autoconfigure  : all the real code: auto-configuration, template, queries, repos
solr-spring-boot-data-curator   : a utility tool for building data for the sample app (not important)
solr-spring-boot-starter        : thin POM that consumers depend on
solr-spring-boot-sample         : demo Spring Boot app with Docker Compose support
```

## Quick Start

### Prerequisites

![jean-luc-java.png](assets/jean-luc-java.png)

- JDK 21+
- Docker (for integration tests and the sample app)
- No Maven installation needed : the project includes the Maven Wrapper

Note: I would recommend jenv for managing your JDK per project https://www.jenv.be/

### Build

```bash
./mvnw clean verify                    # full build + tests + coverage
./mvnw test                            # unit tests only
./mvnw test -pl solr-spring-boot-autoconfigure  # tests in autoconfigure module only
```

### Run the Sample App

```bash
./mvnw spring-boot:run -pl solr-spring-boot-sample
```

This auto-starts a Solr 10 container with a pre-created `books` collection.

The app exposes book REST endpoints `/api/books`

There is also the usual Actuator health endpoints at `/actuator/health`.

### Live Demo

The bookstore sample app is deployed and running on Railway:

**[https://spring-data-solr-sample-book-production.up.railway.app](https://spring-data-solr-sample-book-production.up.railway.app)**

- Swagger UI: `/docs`
- OpenAPI spec: `/api-docs`
- Health: `/actuator/health`

Want your own instance? One click gets you a Railway project with both Solr and the app wired together:

[![Deploy on Railway](https://railway.com/button.svg)](https://railway.com/deploy/spring-data-solr?referralCode=_GAcKG&utm_medium=integration&utm_source=template&utm_campaign=generic)

Set `SPRING_SOLR_STANDALONE_HOST` to the internal Railway URL of your Solr service once deployed.

### Use in Your Project

Add the starter dependency (once published to Maven Central):

```xml

<dependency>
  <groupId>com.tomaytotomato</groupId>
  <artifactId>solr-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Configure in `application.yml`:

```yaml
# Standalone Solr (uses HttpJdkSolrClient, zero extra dependencies)
spring:
  solr:
    standalone:
      host: http://localhost:8983/solr
      default-collection: myCollection
    connection-timeout: 10s
    request-timeout: 60s
    commit-mode: none          # or 'immediate' to hard-commit after each write
```

```yaml
# SolrCloud (uses CloudSolrClient with ZooKeeper)
spring:
  solr:
    cloud:
      zk-host: zk1:2181,zk2:2181,zk3:2181
      default-collection: myCollection
```

Configuring both `standalone` and `cloud` will cause a startup error.

If you need full control (custom TLS, `HttpJettySolrClient`, etc.), define your own `SolrClient`
bean and the auto-configuration backs off entirely.

Define a repository:

```java

@SolrEntity(collection = "books")
public class Book {

  @Field
  String id;
  @Field
  String title;
  @Field
  String author;
}

public interface BookRepository extends SolrRepository<Book, String> {

  List<Book> findByAuthor(String author);

  List<Book> findByTitleContaining(String keyword);

  long countByAuthor(String author);

  @Query("title:?0 AND author:?1")
  List<Book> findByTitleAndAuthorCustom(String title, String author);
}
```

## Links and References

- [A picture of Josh Long](https://joshlong.com/img/josh-hero-image.2ac6dba0.png)
- [Architecture](docs/ARCHITECTURE.md)
- [Common Questions](docs/COMMON-QUESTIONS.md)
- [Dev Log](docs/DEVLOG.md)
- [Limitations of the Original](docs/LIMITATIONS.md)
- [Original Project (Spring Attic)](https://github.com/spring-attic/spring-data-solr)
- [Spring Boot Starter Template](https://github.com/ericus20/spring-boot-starter)
- [Apache SolrJ Reference](https://solr.apache.org/guide/solr/latest/deployment-guide/solrj.html)
