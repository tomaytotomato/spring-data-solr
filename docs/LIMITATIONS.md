# Spring Data Solr — Why It Died, What We Resurrect

## Archive Timeline

- Discontinued April 7, 2020 by Spring team (Christoph Strobl)
- Reason: declining community interest, no maintainers
- Last release: spring-data-solr 4.3.15 (Nov 2021)
- Spring Boot starter removed in Boot 2.5.0-M1
- GitHub repo archived September 19, 2023 → spring-attic/spring-data-solr
- Spring recommended: "use Elasticsearch or raw SolrJ"

## Supported Stack (at death)

- Java 8+, Spring Framework 5.3.x, Spring Boot 2.x only
- SolrJ 6.6–8.x (never supported Solr 9 or 10)
- Incompatible with Spring Boot 3.x, Spring Framework 6, JDK 17+

## Known Bugs (unfixed at archival)

- **DATASOLR-590**: SolrRepositoryFactoryBean initialisation failure — the factory bean failed to initialise under certain Spring context configurations, causing application startup to fail
- **DATASOLR-588**: Incorrect pagination totals with offset — `Page` results returned wrong `totalElements` counts when an offset was applied, making reliable pagination impossible
- **DATASOLR-444**: Nested objects indexed as String representation — nested document objects were serialised as their `toString()` output rather than being properly indexed as structured data
- **DATASOLR-402**: Missing collection name in SingleEntityExecution since M3 — single-entity repository methods lost the collection name in the query execution path, causing routing failures
- **DATASOLR-363**: SolrTemplate appends class name to URL causing 404s — the template incorrectly appended the entity class name to the Solr endpoint URL, breaking all requests to the core
- **DATASOLR-304**: `@SolrDocument solrCoreName` doesn't support PropertyPlaceholders — core names had to be hardcoded; `${property.key}` expressions in `@SolrDocument` were never resolved (now `@SolrEntity`)
- **DATASOLR-248**: Multivalued fields not parsed correctly — collection-type fields (`List<String>`, etc.) were not correctly deserialised from Solr responses, returning single values or empty collections
- **DATASOLR-237**: Dynamic field mapping broken — dynamically named Solr fields (e.g. `*_s`, `*_i` patterns) could not be mapped to Java fields reliably

## Feature Gaps

- No `@Field` name resolution in derived queries (the #1 pain point — a property named `year` with `@Field("publication_year")` generates `year:` in the query instead of `publication_year:`)
- No JSON Facet API (only the legacy facet API was supported; the modern JSON Facet API introduced in Solr 5 was never integrated)
- No cursor-based deep paging in the repository abstraction (deep pagination required raw SolrJ, bypassing the Spring Data layer entirely)
- No streaming expressions
- No Suggester / autocomplete integration
- No schema API integration
- Weak SolrCloud support (no collection management, aliases, or routing configuration)
- No audit annotations (`@CreatedDate`, `@LastModifiedDate`)
- Custom converter registration was manual-only (no auto-detection or annotation-driven registration)

## Community Fork

NET-A-PORTER published a maintained fork (`io.github.net-a-porter:spring-data-solr:5.1.5`) that updated the library to Spring Framework 6 and Spring Data Commons 3.1. It remains the most viable path for teams unable to move away from the original architecture. However, it still targets SolrJ 8.7 and inherits all the architectural limitations above — including the `@Field` name resolution gap and the absent JSON Facet API. It is not a ground-up redesign.

## What Lazarus Brings

- Modern stack: JDK 25, Spring Boot 4.0.6, Spring Framework 7, SolrJ 10.0.0
- `HttpJdkSolrClient` as zero-dependency default — no Jetty runtime required
- `@Field` name mapping in derived queries — fixed from day one, not deferred
- `@Query` annotation for raw Solr query strings with parameter substitution
- Highlighting support (`HighlightPage`, configurable pre/post tags)
- Faceting support (field facets, query facets, range facets)
- Cursor-based deep paging integrated with Spring Data
- Testcontainers-first testing (real Solr, not embedded)
- Annotation-only configuration — no XML namespace config required
- Spring Boot 4 auto-configuration with proper health indicators
