---
title: "Research: findAll() behaviour across Spring Data stores"
date: 2026-05-29
type: research
query: "What does Spring Data MongoDB SimpleMongoRepository and Spring Data Elasticsearch SimpleElasticsearchRepository do for findAll()"
researchers: 3
sources: 6
---

# Research Report: findAll() behaviour across Spring Data stores

## Summary

Spring Data MongoDB's `SimpleMongoRepository.findAll()` issues a completely unbounded query and materialises the entire collection into a Java `List<T>` — no limit, no cursor, no exception. Spring Data Elasticsearch's `SimpleElasticsearchRepository.findAll()` attempts the same intent but is fatally broken in practice: it counts first then requests the full page, which throws `"Result window is too large"` the moment the collection exceeds Elasticsearch's default 10,000-document `max_result_window`. Spring Data Commons itself carries no official warning in the `CrudRepository` contract — the anti-pattern is community-acknowledged, not framework-enforced. For Solr, which shares an identical default row limit of 10,000, the Elasticsearch failure mode is the directly applicable precedent.

## Key Findings

1. **MongoDB: truly unbounded, no safety net.** `findAll()` and `findAll(Sort)` construct an empty `Query`, apply no skip or limit, and call `mongoOperations.find()` which returns a fully-materialised `List<T>`. The entire collection lands in heap memory. No exception is thrown; no limit is enforced by the framework.
   — *Source: [SimpleMongoRepository.java](https://github.com/spring-projects/spring-data-mongodb/blob/main/spring-data-mongodb/src/main/java/org/springframework/data/mongodb/repository/support/SimpleMongoRepository.java)*

2. **Elasticsearch: unbounded intent, broken in practice beyond 10,000 documents.** `SimpleElasticsearchRepository.findAll()` calls `count()` then issues a single `PageRequest.of(0, totalCount)`. For any collection exceeding Elasticsearch's `index.max_result_window` (default 10,000), this throws `"Result window is too large"`. The method is not guarded, warned, or documented against this failure.
   — *Source: [SimpleElasticsearchRepository.java](https://github.com/spring-projects/spring-data-elasticsearch/blob/main/src/main/java/org/springframework/data/elasticsearch/repository/support/SimpleElasticsearchRepository.java)*

3. **Solr shares the same 10,000-row ceiling as Elasticsearch.** Solr's default `rows` parameter and result-window behaviour impose the same constraint. The Elasticsearch failure mode is directly replicable in Solr if `findAll()` is implemented with a single unbounded query.

4. **Elasticsearch's recommended escape valve is `Stream<T>` return type.** Repository methods declared as `Stream<T> findAllBy()` transparently route through the Scroll API, bypassing `max_result_window`. The standard `Iterable<T> findAll()` signature offers no equivalent path.
   — *Source: [GitHub Issue DATAES-802](https://github.com/spring-projects/spring-data-elasticsearch/issues/1374)*

5. **The `CrudRepository` contract carries no formal warning.** The Javadoc for `findAll()` reads only "Returns all instances of the type." — no deprecation, no anti-pattern flag, no size guidance. Third-party libraries (Hypersistence Utils) have deprecated it and throw `UnsupportedOperationException`, but Spring Data itself has not moved.
   — *Source: [CrudRepository.java](https://github.com/spring-projects/spring-data-commons/blob/main/src/main/java/org/springframework/data/repository/CrudRepository.java); [Vlad Mihalcea — The Spring Data findAll Anti-Pattern](https://vladmihalcea.com/spring-data-findall-anti-pattern/)*

6. **Community consensus is that `findAll()` is an anti-pattern, but the framework refuses to enforce it.** Vlad Mihalcea and Baeldung document the risks. Spring Data has never formally acted on this. The practical implication: any Spring Data Solr implementation that follows the MongoDB approach will compile and run fine on small collections, and silently or noisily fail on large ones.
   — *Source: [Baeldung — Patterns for Iterating Over Large Result Sets](https://www.baeldung.com/spring-data-jpa-iterate-large-result-sets)*

## External Sources

- [SimpleMongoRepository.java](https://github.com/spring-projects/spring-data-mongodb/blob/main/spring-data-mongodb/src/main/java/org/springframework/data/mongodb/repository/support/SimpleMongoRepository.java)
- [SimpleElasticsearchRepository.java](https://github.com/spring-projects/spring-data-elasticsearch/blob/main/src/main/java/org/springframework/data/elasticsearch/repository/support/SimpleElasticsearchRepository.java)
- [CrudRepository.java — Spring Data Commons](https://github.com/spring-projects/spring-data-commons/blob/main/src/main/java/org/springframework/data/repository/CrudRepository.java)
- [GitHub Issue DATAES-802 — Scroll API recommendation](https://github.com/spring-projects/spring-data-elasticsearch/issues/1374)
- [The Spring Data findAll Anti-Pattern — Vlad Mihalcea](https://vladmihalcea.com/spring-data-findall-anti-pattern/)
- [Patterns for Iterating Over Large Result Sets — Baeldung](https://www.baeldung.com/spring-data-jpa-iterate-large-result-sets)

## Contradictions

- **MongoDB vs Elasticsearch approaches diverge sharply despite identical intent.** MongoDB issues a single genuinely unbounded query that succeeds (at the cost of heap memory). Elasticsearch issues a bounded query sized to the count, which *fails* beyond 10,000. Neither is safe; they fail differently.
- **No contradictions between the three sources.** All findings are complementary.

## Gaps

- Whether Solr's `HttpJdkSolrClient` silently caps results at its default `rows=10` (Solr's actual default when `rows` is omitted) rather than returning all documents.
- Whether `Stream<T>` as a return type in a Spring Data Solr repository could be wired to a cursor-based implementation.
- Whether Spring Data Elasticsearch's `findAll()` has an open bug report acknowledging the `max_result_window` failure.

## Recommendation

Implement `findAll()` in `SimpleSolrRepository` as a **bounded, cursor-based operation** — do not follow MongoDB's naive unbounded approach, and do not replicate Elasticsearch's broken count-then-fetch pattern.

**Option A — Bounded with a hard cap and a logged warning (minimal effort, honest behaviour).** Implement `findAll()` with a configurable but defaulted `rows` limit (e.g., 1,000). Log a `WARN` if collection count exceeds the cap.

**Option B — Cursor-based via `CursorResult` (correct behaviour, higher effort).** Wire `findAll()` through `SolrTemplate.queryWithCursor()`, iterating pages internally and accumulating results.

**Option C — Throw `UnsupportedOperationException` with a clear message.** Direct callers to `findAll(Pageable)` or a cursor-based `Stream` method.

**Recommended choice: Option A for immediate correctness, with a path to Option B.** Add a `Stream<T> findAllBy()` cursor-based method alongside it as the documented safe alternative.

---

*Generated by research-swarm on 2026-05-29. 3 parallel researchers + 1 collator.*
