# Spring Data Solr Lazarus — Dev Log

A modern Spring Boot starter for Apache Solr 10, resurrecting the archived `spring-data-solr` project.

---

## 2026-05-12 — Day 1: From Zero to Feature-Complete

### Session 1: Scaffolding & Core (20:00–20:30)

**Commits:** `ddc66c7` → `45885b0`

Set up the multi-module Maven project: parent POM with Spring Boot 3.5.0 BOM, SolrJ 10.0.0, Java 21.

- `solr-spring-boot-autoconfigure` — the real work
- `solr-spring-boot-starter` — thin starter POM
- `solr-spring-boot-sample` — demo app

Core auto-configuration: `SolrProperties` binding `spring.solr.*`, `SolrAutoConfiguration` creating `HttpJdkSolrClient` (standalone) or `CloudSolrClient` (when `zkHost` is set). `SolrTemplate` wrapping SolrJ with save/find/query/delete. Sample app with `BookController`. Docker Compose support with Solr 10 container.

**Gotcha:** SolrJ 10 split into three artifacts (`solr-solrj`, `solr-solrj-jetty`, `solr-solrj-zookeeper`). `Http2SolrClient` renamed to `HttpJettySolrClient`. `HttpJdkSolrClient` is the zero-dependency default. Timeout API is `(long, TimeUnit)` not `(int)`.

### Session 2: Critique-Driven Enrichment (22:00–23:00)

**Commits:** `adb926a` → `c4bd35f`

Ran a critique agent against the initial implementation. It flagged the library as "thin" — just a wrapper around SolrJ with no real Spring Data value-add. Fair point. Went deep and built:

- **Criteria API** — fluent query builder (`Criteria.where("title").contains("spring").and("price").greaterThan(10)`)
- **SimpleQuery** — wraps Criteria with pagination, sort, filters, projections → converts to SolrQuery
- **SolrPage** — extends Spring Data `PageImpl` with Solr's `maxScore`
- **@SolrDocument / @Score annotations** — `SolrDocumentResolver` for collection name resolution
- **PartialUpdate** — atomic field updates (set/add/increment) via SolrInputDocument
- **Spring Data Repository abstraction** — `SolrRepository<T,ID>` extending `PagingAndSortingRepository` + `CrudRepository`, with `SimpleSolrRepository`, `SolrRepositoryFactory`, `@EnableSolrRepositories`, and auto-configuration for zero-config scanning
- **SolrHealthIndicator** — collection-aware ping with admin endpoint fallback
- **Testcontainers integration tests** — 4 tests with graceful skip when Docker unavailable

**Gotchas discovered:**
- `NamedList.findRecursive` doesn't exist in Solr 10 — used pattern matching `instanceof NamedList<?>` instead
- `GenericSolrRequest` uses `SolrRequest.SolrRequestType.ADMIN` not its own inner type
- `CloudSolrClient` IS in core `solr-solrj` (not just the zookeeper module) — no reflection needed
- Health indicator needs `@ConditionalOnEnabledHealthIndicator("solr")` to respect `management.health.solr.enabled`
- Rolled our own `escapeQueryChars` initially — replaced with `ClientUtils.escapeQueryChars`

**End of day:** 120 tests (116 unit + 4 integration), BUILD SUCCESS.

---

## 2026-05-13 — Day 2: Derived Query Methods

### Session 1: PartTree → Solr Queries (08:30–09:10)

**Commit:** `74b8bac`

Implemented Spring Data derived query method support — the feature that makes repository interfaces feel magical. Declare `findByTitleContaining(String)` and the Solr query writes itself.

**Architecture:**

```
Repository method name
        ↓
   PartTree (Spring Data Commons)
        ↓
   SolrQueryCreator (AbstractQueryCreator<SimpleQuery, Criteria>)
        ↓
   SimpleQuery → SolrQuery
        ↓
   SolrTemplate.query() / queryForPage() / count()
```

**New classes:**
- `SolrQueryCreator` — translates PartTree keywords to Criteria predicates. 18 Part.Types supported.
- `PartTreeSolrQuery` — implements `RepositoryQuery`, handles return type dispatch (List, Page, single, count, exists)
- `SolrQueryLookupStrategy` — plugs into `SolrRepositoryFactory.getQueryLookupStrategy()`

**Criteria enhancements:**
- `and(Criteria)` / `or(Criteria)` — combine independently-built Criteria chains (needed for OR branches in PartTree)
- `notContains(String)` — negated wildcard matching for `NOT_CONTAINING`
- `SimpleQuery.setPageable()` / `getCriteria()` — accessors for query reuse

**Supported keywords:** Is, Not, Containing, NotContaining, StartingWith, EndingWith, GreaterThan, GreaterThanEqual, LessThan, LessThanEqual, Between, In, IsNull, IsNotNull, True, False, Before, After

**Return types:** `List<T>`, `Page<T>`, single entity, `long` (count), `boolean` (exists)

**Sample app updated:** `BookRepository` now declares derived methods:
```java
List<Book> findByAuthor(String author);
List<Book> findByTitleContaining(String title);
Page<Book> findByAuthorAndYearGreaterThan(String author, int year, Pageable pageable);
long countByAuthor(String author);
boolean existsByTitle(String title);
```

**Tests:** 31 new (21 SolrQueryCreator + 7 PartTreeSolrQuery + 3 Criteria chain combinators). **151 total, 0 failures.**

**Known limitation:** Derived queries use Java property names as Solr field names. If a field has `@Field("publication_year")` but the property is `year`, the derived query queries `year:` not `publication_year:`. Proper @Field mapping is a future enhancement.

### Session 2: Migrate to Spring Boot 4.0.6 + JDK 25 (09:30–10:00)

**Commit:** `1993868`

Bumped from Spring Boot 3.5.0 → 4.0.6, Spring Data Commons 3.5.0 → 4.0.5, targeting JDK 25 (LTS).

**Breaking changes resolved:**

| What moved | Old (Boot 3.5) | New (Boot 4.0) |
|------------|----------------|----------------|
| Health classes | `boot.actuate.health` | `boot.health.contributor` |
| Health auto-config | `boot.actuate.autoconfigure.health` | `boot.health.autoconfigure.contributor` |
| Actuator dependency | `spring-boot-actuator-autoconfigure` | `spring-boot-health` |
| Query lookup strategy | `QueryMethodEvaluationContextProvider` | `ValueExpressionDelegate` |
| Testcontainers artifacts | `solr`, `junit-jupiter` | `testcontainers-solr`, `testcontainers-junit-jupiter` |
| Testcontainers BOM | managed by Boot | explicit `testcontainers-bom` 2.0.5 required |

**Gotcha:** `PropertyPath` moved from `org.springframework.data.mapping` to `org.springframework.data.core` in Spring Data 4.0. Didn't require a code change (we only call `part.getProperty().getSegment()`) but caused a stale-classpath `NoSuchMethodError` until a clean build was run. Lesson: always `mvn clean` after a major BOM bump.

**Also fixed:** `SolrIntegrationTest.TestBook` was package-private — SolrJ's `DocumentObjectBinder` couldn't instantiate it. Made it `public`. This was a latent bug hidden because Docker wasn't running in earlier sessions.

**Tests:** 158 total (147 unit + 11 integration), 0 failures. Full BUILD SUCCESS on JDK 25.0.3 (Temurin LTS).

**Build verified:** `mvn clean verify` — all modules compile and pass on JDK 25, Spring Boot 4.0.6. `.java-version` pinned via jenv.

### Session 3: Claude Code Setup & Documentation (13:00–14:00)

**Commits:** `839bf53` → `992fdc2`

Shifted focus from code to developer experience. Pinned JDK 25 via jenv (`.java-version`), added a
README with project motivation and reference links, and created `CLAUDE.md` — a comprehensive
guidance file for Claude Code covering build commands, module structure, architecture, SolrJ 10
gotchas, and the known `@Field` mapping limitation.

Also created a `devlog` skill for the `bruce/personal` Claude Code plugin to enforce the discipline
of updating this file after every work session. The skill gathers git context automatically and
follows the established entry format.

- Added `.java-version` pinned to 25.0 for jenv
- Created README.md with project overview, motivation, and reference links
- Created CLAUDE.md with full architecture documentation
- Built `devlog` skill (SKILL.md) for Claude Code personal plugin
- Verified full build passes on JDK 25.0.3 (Temurin LTS)

**Gotcha:** Claude Code cannot write to `.claude/settings.json` directly — the harness blocks
self-modification of permission files. Settings must be created manually or committed as part of
the repo.

**Tests:** 158 total (147 unit + 11 integration), 0 failures. No test changes this session.

---

## 2026-05-13 — Day 2 (continued): Feature Blitz & CI

### Session 4: Full Feature Implementation with Parallel TDD Agents (14:00–14:30)

**Commits:** `e23fdd1` → `e1a9ed7`

Massive feature session — implemented six major features in parallel using Claude Code agents with
git worktrees and direct-to-tree writes. The approach: research first (old spring-data-solr
limitations, current dependency versions), then dispatch TDD agents for each feature simultaneously.

**Features delivered:**

- **`@Field` name mapping** (`f021b65`) — `SolrFieldNameResolver` scans `@Field` annotations at
  class-load time, caches per entity type. `SolrQueryCreator` now resolves field names through it.
  The #1 pain point from the original spring-data-solr, fixed. 5 new tests.

- **`@Query` annotation** (`2e4cc93`) — `StringBasedSolrQuery` executes raw Solr query strings with
  `?0`, `?1` parameter substitution. `SolrQueryLookupStrategy` routes `@Query`-annotated methods to
  it, falls back to `PartTreeSolrQuery`. Added `count` attribute for count queries. Also added
  `count(String, SolrQuery)` overload to `SolrTemplate` — the `SimpleQuery` variant now delegates to
  it. 8 new tests.

- **Highlighting** (`8a513f4`) — `HighlightPage<T>`, `HighlightEntry<T>` record, `HighlightOptions`
  (pre/post tags, snippets, fragsize). `SolrTemplate.queryForHighlightPage()` pairs docs with
  highlight snippets by document ID. Uses `hl.tag.pre`/`hl.tag.post` (unified highlighter). ~36 new
  tests.

- **Faceting** (`c222225`) — `FacetPage<T>`, `FacetFieldEntry`/`FacetQueryEntry` records,
  `FacetOptions` (field facets, query facets, minCount, limit, sort). Parses SolrJ `FacetField` and
  facet query map from `QueryResponse`. Uses `FacetParams` constants. ~32 new tests.

- **Cursor-based deep paging** (`8c74635`) — `CursorResult<T>` record wrapping Solr's cursorMark.
  `hasMore` detects exhaustion when `nextCursorMark == requestCursorMark`. Documented the Solr
  requirement for sort to include uniqueKey field. ~11 new tests.

- **JaCoCo code coverage** (`22e3540`) — `jacoco-maven-plugin` 0.8.13 with 80% LINE coverage
  threshold on autoconfigure module. Starter and sample modules skip JaCoCo. Added 6 tests to
  SolrTemplateTest to cover previously untested branches (annotated collection methods, soft commit,
  commit mode immediate). Coverage at 81.7%.

**Infrastructure:**
- **GitHub Actions CI** (`272cfd9`) — `.github/workflows/ci.yml` triggers on push/PR to master. JDK
  25 Temurin, Maven cache, JaCoCo report upload as artifact.
- **README overhaul** — badges (CI, Java, Spring Boot, SolrJ, License), feature list, quick start
  guide, usage examples with code snippets.
- **LIMITATIONS.md** (`e23fdd1`) — comprehensive documentation of the archived spring-data-solr
  project's bugs, gaps, and what Lazarus improves.

**Sample app enhanced** (`e1a9ed7`) — DataLoader seeds 10 books on startup. BookRepository
demonstrates derived queries, `@Query` with parameter substitution, count queries. BookController
showcases CRUD, highlighting, faceting, cursor paging, and statistics endpoints.

**Gotchas discovered:**
- Parallel agents writing to the same file (SimpleQuery.java) required manual integration — three
  agents each added their feature (highlight/facet/cursor) but the last writer's version needed the
  other two features merged in
- `FacetParams` lives in `org.apache.solr.common.params.FacetParams`, not nested on `SolrQuery`
- Worktree agents can commit directly to the main branch — the worktree isolation prevents file
  conflicts but not branch conflicts. Merging worktree branches into master with concurrent
  direct-to-tree writes required stash/merge/pop choreography
- `mvn clean` fails on locked surefire-reports directory when agents hold file handles — use
  `mvn verify` without clean, or wait for agents to complete

**Tests:** 256 total (245 unit + 11 integration), 0 failures. JaCoCo coverage gate passes at >80%.

### Session 5: Multi-Version Integration Tests (14:30–14:45)

**Commit:** `a74f812`

Refactored integration tests to run against both Solr 9 and Solr 10. The original `SolrIntegrationTest`
used a hardcoded `solr:9` image — a leftover from the initial scaffolding session, never upgraded to
match the SolrJ 10 dependency. Rather than simply bumping to `solr:10`, took the smarter approach:
prove backward compatibility by testing both versions.

Extracted all test logic into `AbstractSolrIntegrationTest` (abstract base), then created two thin
concrete subclasses — `Solr9IntegrationTest` and `Solr10IntegrationTest` — each specifying only the
Docker image version. JUnit 5 discovers `@Nested` inner classes from superclasses, so all test
groupings (SaveAndRetrieve, QueryOperations, DeleteOperations, HealthIndicator) are inherited
automatically. Each subclass gets its own Testcontainers lifecycle.

Also consolidated the two `@BeforeEach` methods (`setUp` and `cleanCollection`) into a single method
to eliminate a latent ordering dependency — JUnit 5 does not guarantee execution order of multiple
`@BeforeEach` methods within the same class.

**Gotchas discovered:**
- JUnit 5 DOES discover `@Nested` inner classes declared in abstract superclasses — `findNestedClasses`
  traverses the full class hierarchy. This makes the abstract base + concrete subclass pattern viable
  for multi-version Testcontainers testing.
- `SolrContainer` in Testcontainers 2.0.5 is fully compatible with both `solr:9` and `solr:10` images.
  Version-aware logic (Zookeeper startup commands at 9.7.0+), collection creation, and Jetty startup
  detection all work unchanged across both versions.

**Tests:** 267 total (245 unit + 22 integration), 0 failures. JaCoCo coverage gate passes at >80%.

### Session 6: Polish, Fixes & Developer Experience (15:00–16:40)

**Commits:** `4932c4a` → `8cde7e9`

Housekeeping session — restored personality to the README after the Session 4 agents had steamrolled
Bruce's narrative intro with a generic one-liner. Merged both versions: Bruce's voice for the opening
hook and "Why?" motivation, the agent's technical depth for Features/Tech Stack/Quick Start. Added a
Prerequisites section and a Solr `9 | 10` compatibility badge.

Added the Maven Wrapper (`mvnw` / `mvnw.cmd`, Maven 3.9.15) so contributors can build without a local
Maven installation. All README build commands now reference `./mvnw` instead of `mvn`.

Added `paths-ignore` to the GitHub Actions CI workflow — pushes that only touch markdown, assets,
license, `.gitignore`, `.java-version`, or `.claude/` config no longer trigger a build.

Fixed a startup-crashing bug in the sample app: `@EnableSolrRepositories` was missing `includeFilters`,
`excludeFilters`, and `bootstrapMode` attributes. Spring Data Commons 4.0.5's
`AnnotationRepositoryConfigurationSource.hasExplicitFilters()` reads these via `getAnnotationArray()`
and throws `IllegalArgumentException` when they're absent. This was a latent bug — the unit and
integration tests never exercised the full auto-configuration boot path that the sample app triggers.

Deeper than initially expected — after fixing `includeFilters`/`excludeFilters`, two more startup
failures surfaced in sequence:

- `NoClassDefFoundError: InvalidDataAccessApiUsageException` — `spring-tx` is declared `<optional>true`
  in `spring-data-commons` 4.0.5, so it doesn't flow transitively. Added it explicitly to the starter
  POM.
- `NullPointerException: solrTemplate is null` in `SimpleSolrRepository.deleteAll()` —
  `SolrRepositoryConfigurationExtension` was missing a `postProcess()` override to wire `solrTemplate`
  into the `SolrRepositoryFactoryBean` via `addPropertyReference`. Without this, Spring Data creates the
  factory bean but never injects the template. Added both the generic `RepositoryConfigurationSource` and
  the `AnnotationRepositoryConfigurationSource` overloads (the latter reads `solrTemplateRef` from the
  annotation attributes).

The full `@EnableSolrRepositories` annotation now declares all attributes that
`AnnotationRepositoryConfigurationSource` reads: `repositoryFactoryBeanClass`, `queryLookupStrategy`,
`namedQueriesLocation`, `repositoryImplementationPostfix`, and `considerNestedRepositories` — in
addition to the earlier `includeFilters`, `excludeFilters`, and `bootstrapMode`. Attribute names were
extracted by decompiling `AnnotationRepositoryConfigurationSource` bytecode and scanning for `ldc`
string constants. The `fragmentsContributor` attribute is guarded by `containsKey` and can be omitted.

After all fixes: `Started SampleApplication in 2.8s`, `Loaded 10 sample books into Solr`. Full
end-to-end boot confirmed.

**Gotchas discovered:**
- Spring Data Commons 4.0.5 `AnnotationRepositoryConfigurationSource` reads 12+ attributes from the
  enable annotation via `getRequiredAttribute` — any missing attribute causes `IllegalArgumentException`
  at startup. The old spring-data-solr didn't need these because Spring Data Commons 3.x didn't read
  them in the same code path. This is undocumented in the Spring Data 4.0 migration guide.
- `spring-tx` is `<optional>true` in `spring-data-commons` — starters must pull it in explicitly.
- `RepositoryConfigurationExtensionSupport.postProcess()` is the wiring point for custom template
  references. Without it, the factory bean creates repositories with null collaborators. Unit tests
  don't catch this because they construct `SimpleSolrRepository` directly with constructor injection.
- Running `mvn spring-boot:run -pl solr-spring-boot-sample` from the repo root causes a Docker Compose
  file lookup failure because the working directory is the repo root, not the module directory. The
  Docker Compose support resolves paths relative to `user.dir`.

**Tests:** 267 total (245 unit + 22 integration), 0 failures.

### Session 7: Runtime Fixes — Compiler Flags & Schema Mapping (16:50–17:10)

**Commit:** `faaebcd`

Two more runtime errors surfaced when actually exercising the sample app's REST endpoints — the kind
of bugs that only appear when the full request path executes, not at startup.

First: `@RequestParam` and `@PathVariable` bindings failed with missing parameter names. Spring
Framework 7 (used by Boot 4.0.6) no longer infers parameter names from bytecode debug info — it
requires the `-parameters` compiler flag. Added `<parameters>true</parameters>` to the
`maven-compiler-plugin` configuration in the parent POM.

Second: Solr's default schemaless ("managed") mode auto-creates bare field names (e.g. `title`,
`author`) as `multiValued="true"`. When SolrJ deserialises the response, it returns `ArrayList`
instead of `String`, causing `IllegalArgumentException: Can not set String field to ArrayList`.
Fixed by switching all `@Field` annotations in the Book entity to Solr's dynamic field suffixes:
`title_s`, `author_s`, `year_i`, `price_d`, `genre_s`. Updated all `@Query` annotations in
`BookRepository` and raw field references in `BookController` (highlight, facet queries).

Also removed the explicit `file: docker-compose.yml` property from `application.yml` — Spring Boot's
Docker Compose auto-detection finds `docker-compose.yml` in the working directory without it.

After fixes: all sample app endpoints verified end-to-end — `/api/books` returns 10 books,
`/api/books/stats` returns correct counts, `/api/books/search?q=Spring` filters correctly.

**Gotchas discovered:**
- Spring Framework 7 requires `-parameters` for `@RequestParam`/`@PathVariable` name resolution —
  without it, parameter names are `arg0`, `arg1` and binding silently fails. This is a Boot 4.x
  migration gotcha not immediately obvious from compiler output.
- Solr's default managed schema creates bare field names as `multiValued="true"`. Use dynamic field
  suffixes (`_s`, `_i`, `_d`, `_l`, `_b`, `_dt`) to get single-valued fields in schemaless mode.
  Alternatively, define an explicit schema — but dynamic suffixes are the path of least resistance
  for a starter library sample.

**Tests:** 267 total (245 unit + 22 integration), 0 failures.

### Session 8: Package Rename — com.tomaytotomato.data.solr (17:20–17:50)

**Commit:** `a8f8651`

Renamed all Maven coordinates and Java packages to align with the `com.tomaytotomato` domain used
for published artifacts (e.g. `location4j`). The "Lazarus" branding stays in the repo name and
README but no longer leaks into import statements or POM coordinates.

**What changed:**
- groupId: `dev.solrlazarus` → `com.tomaytotomato`
- Parent artifactId: `spring-data-solr-lazarus` → `spring-data-solr`
- Java packages: `dev.solrlazarus.autoconfigure` → `com.tomaytotomato.data.solr`
- Sample app: `dev.solrlazarus.sample` → `com.tomaytotomato.data.solr.sample`
- Auto-configuration imports file updated to new FQCNs
- README dependency snippet updated

The package structure mirrors the original `org.springframework.data.solr` — just under our domain.
Module directory names (`solr-spring-boot-autoconfigure`, etc.) stayed the same since they never
contained "lazarus".

Also added a symlink at the repo root (`docker-compose.yml → solr-spring-boot-sample/docker-compose.yml`)
so Docker Compose commands work from either the repo root or the module directory.

71 files touched — all renames plus package declaration / import updates. Verified with `git mv` for
clean rename tracking in git history.

**Tests:** 267 total (245 unit + 22 integration), 0 failures. BUILD SUCCESS.

---

### Session 9: Pre-Release Code Reviews (18:00–18:30)

**No commits — review session only.**

Ran two simulated code reviews before considering public release — one from the perspective of Rod
Johnson (architectural rigour, Spring Data conventions) and one from Josh Long (developer experience,
demo-ability, ecosystem citizenship). Both reviewed every production and test source file.

**Key finding:** Both reviewers independently converged on the same top-5 issues — a strong signal
these are real problems, not stylistic preferences.

Full reviews saved in [`docs/reviews/`](docs/reviews/):
- [`rod-johnson-review.md`](docs/reviews/rod-johnson-review.md) — architectural review
- [`josh-long-review.md`](docs/reviews/josh-long-review.md) — developer experience review

**Consensus strengths:**
- Auto-configuration chain follows Boot conventions precisely
- Testcontainers dual-version testing pattern is excellent
- `@Field` name mapping in derived queries (the original's #1 pain point) is solid
- `CursorResult` record design is correct
- `SolrFieldNameResolver` thread-safe caching is well done

**On Solr 8 backwards compatibility:** Both said no. The project's premise is "the original died on
Solr 8; we start at 9." Teams on Solr 8 have the NET-A-PORTER fork. Adding backwards compatibility
would dilute the codebase and betray the narrative.

**Tests:** 267 total (245 unit + 22 integration), 0 failures. No test changes this session.

---

## What's Next (post Day 2)

### Pre-Release: Must Fix — DONE

- [x] **Solr injection in `StringBasedSolrQuery.resolveParameters`** — escaped with
      `ClientUtils.escapeQueryChars()` (`2731de1`)
- [x] **`SolrEntityInformation.getId()` returns null** — `@Field`-based ID reflection implemented
      (`f330fd8`)
- [x] **`delete(entity)` / `deleteAll` throw `UnsupportedOperationException`** — implemented via
      `getId()` (`f330fd8`)
- [x] **`count(SolrQuery)` mutates caller's object** — defensive copy via `getCopy()` (`bfc945b`)
- [x] **`findAllById` — escape IDs** with `ClientUtils.escapeQueryChars()` (`2731de1`)

### Pre-Release: Should Fix — DONE

- [x] **Remove `getSolrClient()` from `SolrOperations` interface** — kept on `SolrTemplate` as
      concrete method (`bfc945b`)
- [x] **`@SolrDocument` placeholder resolution** — `SolrDocumentResolver` now accepts `Environment`
      (`54b3aa9`)
- [x] **`Criteria.contains/startsWith/endsWith` — escape inner value** before wrapping with
      wildcards (`2731de1`)
- [x] **`SolrFieldNameResolver` — walk class hierarchy** with `putIfAbsent` for subclass precedence
      (`bfc945b`)
- [x] **Add `spring.solr.commit-mode` to `additional-spring-configuration-metadata.json`**
      (`bfc945b`)
- [x] **Repository auto-configuration integration test** — context-level bean wiring verified
      (`54b3aa9`)

### Infrastructure — DONE

- [x] **JDK 21 baseline** — compiler target lowered from 25 to 21, no JDK 22+ features in codebase
      (`6e991a6`)
- [x] **CI matrix** — GitHub Actions now tests on JDK 21 and 25 (`6e991a6`)
- [x] **Dependabot** — weekly scans for Maven dependencies and GitHub Actions versions (`cbbfdac`)
- [x] **README** — SolrCloud/standalone/custom-client configuration documented (`2330300`)

### Session 10: Six Features in Parallel + Coverage Gap Fixes (19:30–20:15)

**Commits:** `19b4416` → `2b22155` (features), then coverage tests on master

Dispatched six worktree agents in parallel to implement the remaining post-release enhancements.
All six completed, merged into master, and passed a unified build. Then closed JaCoCo coverage
gaps flagged in the report — `SolrTemplate.savePartialUpdate` (completely untested),
`CloudSolrClientConfiguration` (0% covered), and missing branch coverage across highlight/facet/
streaming paths.

**Features delivered (parallel agents):**

- **Micrometer instrumentation** (`19b4416`) — `MicrometerSolrTemplate` extends `SolrTemplate`,
  wraps 10 operations with `Timer.record()`. Metric name `solr.operations`, tags `operation` +
  `collection`. Auto-configured via inner `@Configuration` gated on `@ConditionalOnClass` +
  `@ConditionalOnBean(MeterRegistry.class)`. `micrometer-core` is `<optional>true</optional>`.
  20 tests.

- **Cursor/facet/highlight integration tests** (`53cceb1`) — 7 new Testcontainers tests in
  `AbstractSolrIntegrationTest`. Cursor paging: first page, second page different IDs, full
  traversal. Faceting: field facets with author counts, query facets with year ranges.
  Highlighting: snippet presence with `<em>` tags, field key verification. Run on both Solr 9
  and 10.

- **Custom converter pipeline** (`894739c`) — Foundation layer: `SolrReadConverter`,
  `SolrWriteConverter` functional interfaces, `SolrCustomConversions` immutable registry,
  `SolrMappingConverter` wrapper. Auto-configured with `@ConditionalOnMissingBean` for user
  override. Converter *application* during read/write is a deliberate follow-up. 12 tests.

- **Geospatial query support** (`08812d8`) — `GeoPoint` and `GeoDistance` records with unit
  conversion (km/miles). `Criteria.near()` emits `{!geofilt}`, `Criteria.within()` emits
  `{!bbox}`. `SolrQueryCreator` handles `NEAR` and `WITHIN` Part.Types. Raw predicates
  (no `field:` prefix) handled via a `raw` flag on `Predicate`. 13 tests.

- **SolrProperties constructor binding** (`06d3620`) — All fields now `final`, single constructor
  with `@DefaultValue` annotations. No setters. Boot 4 auto-detects constructor binding from the
  single-constructor rule. **CloudSolrClient timeout wiring** — `HttpJdkSolrClient.Builder` (which
  extends `HttpSolrClientBuilderBase`) carries the timeouts, passed via `withHttpClientBuilder()`.
  Existing SolrProperties tests passed without modification.

- **Streaming expressions** (`8fcd0ea`) — `StreamingExpression` fluent builder with `of(raw)` and
  `search(collection)` entry points. `SolrTemplate.stream()` posts to `/stream` via
  `GenericSolrRequest` with `SolrRequestType.STREAMING`. Response parser handles NamedList
  unwrapping and EOF tuple skipping. 14 tests.

**Merge choreography:** Stashed uncommitted DEVLOG, merged Micrometer (fast-forward), integration
tests, then converter pipeline (conflict in `SolrAutoConfiguration` — both Micrometer and converter
added inner configuration classes + imports; resolved by keeping both). Geospatial, SolrProperties,
and streaming merged cleanly. Six merge commits total.

**Coverage gap fixes (on master after merge):**

- `SavePartialUpdate` — 3 tests: verifies `SolrInputDocument` field mapping (id, set, increment),
  `IOException` wrapping, `SolrServerException` wrapping
- `CommitModeImmediate` — 4 new tests: `saveAll`, `savePartialUpdate`, `deleteById`, `deleteByQuery`
  all commit when IMMEDIATE (previously only `save` was tested)
- `QueryForPageWithAnnotatedCollection` — verifies `@SolrDocument` collection resolution in the
  `Pageable` overload
- `QueryForHighlightPage` null docId branch — verifies empty highlights when Solr document has no
  `id` field value
- `Stream` non-List docs — verifies graceful empty result when `docs` is not a `List`
- `Count` SimpleQuery overload — verifies delegation and error wrapping
- `CloudSolrClientConditionalConfiguration` — 3 tests: standard client when no `zk-host`, user
  client precedence when `zk-host` set, context failure when `zk-host` set without real ZK

**Coverage improvement:**

| Class | Before | After |
|-------|--------|-------|
| SolrTemplate lines | 92% (13 missed) | 99% (2 missed) |
| CloudSolrClientConfiguration lines | 0% (10 missed) | 90% (1 missed) |

Remaining misses are catch blocks for exception variants already tested through the `IOException`
path — same wrapping pattern, diminishing returns.

**Gotchas discovered:**
- `CloudSolrClient.Builder.build()` eagerly connects to ZooKeeper — cannot unit test actual bean
  creation without a running cluster. Testcontainers integration tests cover this path. Unit tests
  verify the conditional property activation and user-bean back-off behaviour.
- Six parallel worktree agents that modify the same file (`SolrAutoConfiguration`) will always
  conflict at merge time. Predictable and fast to resolve, but ordering matters — merge the
  simplest diff first to minimise conflict surface for later merges.

**Tests:** 397 total (363 unit + 34 integration), 0 failures. JaCoCo coverage gate passes.

---

## What's Next (post Day 2 Session 10)

### Post-Release: Enhancements — DONE

- [x] **Micrometer instrumentation** — `MicrometerSolrTemplate` with Timer wrapping (`19b4416`)
- [x] **`SolrProperties` constructor binding** — final fields, `@DefaultValue`, no setters
      (`06d3620`)
- [x] **CloudSolrClient timeout wiring** — via `withHttpClientBuilder()` (`06d3620`)
- [x] **Cursor/facet/highlight integration tests** — 7 Testcontainers tests (`53cceb1`)
- [x] **Custom converter pipeline** — foundation layer with auto-configuration (`894739c`)
- [x] **Geospatial query support** — `GeoPoint`, `GeoDistance`, `near()`, `within()` (`08812d8`)
- [x] **Streaming expressions** — fluent builder + `SolrTemplate.stream()` (`8fcd0ea`)

---

## 2026-05-14 — Day 3: Sample App Overhaul & Data Curation Pipeline

### Session 1: Curated Dataset & Full Feature Showcase (09:00–10:10)

**Commits:** (uncommitted — pending ship)

Built a Java-based data curation pipeline and overhauled the sample app to demonstrate every feature
of the starter with realistic data. No Python — the entire pipeline is a new Maven module.

**New module: `solr-spring-boot-data-curator`**

A standalone Java pipeline that transforms a 7k-book Kaggle CSV into a curated 250-book JSON dataset:

- `BookCurator` — main pipeline: read CSV → filter (description≥50 chars, year>0, rating>0) →
  diversify by genre (round-robin allocation: `TARGET_TOTAL / genreCount` per genre, remainder to
  largest) → enrich with geo/pricing → write JSON
- `CategoryNormaliser` — regex-based genre mapping from messy LC subject headings to clean categories.
  Order matters: specific compound genres (True Crime, Science Fiction, Historical Fiction) must
  precede general keyword patterns, otherwise "True Crime" matches "Mystery" via the `crime` pattern
- `BookshopLocations` — maps 17 genre categories to real-world bookshop coordinates (lat/lon/name)
  for geospatial enrichment. Uses `Map.ofEntries()` for fail-fast on duplicate keys
- `PricingStrategy` — category-specific price ranges with page count and rating multipliers
- `CsvBookRecord` / `CuratedBook` — Jackson records for CSV parsing and JSON output

Run with: `./mvnw compile exec:java -pl solr-spring-boot-data-curator`

**Sample app overhaul:**

Expanded `Book` entity from 10 to 15 fields — added subtitle, ratingsCount, location
(LatLonPointSpatialField), locationName, and `@Score` for relevance scoring. `DataLoader` now reads
from `curated-books.json` using Jackson 3.x (`tools.jackson` packages). `BookRepository` expanded
with 12 derived query methods plus `@Query` annotations. `BookController` showcases every starter
feature: CRUD, derived queries, `@Query` methods, highlighting (description + title), faceting
(categories + author), cursor-based deep paging, geospatial (near/within), partial updates
(set price, add category), and stats.

Added custom `managed-schema.xml` with `LatLonPointSpatialField` for the `location` field, mounted
into Docker Compose Solr container.

**Integration tests:** 17 Testcontainers tests across 8 nested groups — DataLoading (count, field
population), DerivedQueries (titleContaining, titleStartingWith, inStock, priceBetween, rating,
yearBetween, countByAuthor, existsByTitle), CustomQueries, Highlighting, Faceting, CursorPaging,
CrudOperations, and PartialUpdates.

**Gotchas discovered:**
- **SolrJ BindingException with schemaless Solr:** Testcontainers `SolrContainer` creates collections
  using the `_default` configset (schemaless mode), NOT our custom `managed-schema.xml`. When the
  `location` field is indexed as `"40.7484,-73.9856"`, schemaless mode auto-detects it as a
  multiValued string. SolrJ then returns `ArrayList` instead of `String`, causing
  `IllegalArgumentException: Can not set String field to ArrayList`. Fixed by pre-defining the field
  via Solr's Schema API (HTTP POST to `/solr/books/schema`) in `@DynamicPropertySource`, which runs
  after container start but before Spring context loads. The `_default` configset already has a
  `location` field TYPE (LatLonPointSpatialField) — we just need to define the FIELD before data
  indexing begins.
- **Text analysis vs string matching in tests:** `findByTitleStartingWith("The")` on a `text_general`
  field matches any document where a token starts with "the" (after lowercasing), not titles
  beginning with "The". Similarly, `existsByTitle("This Title Surely Does Not Exist XYZ123")` returns
  `true` because individual tokens ("title", "exist") match real book titles after analysis. Derived
  query methods delegate to Solr's query parser, which respects the field's analyzer chain.
- **Default pagination:** Solr returns 10 rows by default. `List<T>`-returning repository methods
  don't override this, so `findByInStock(true).size() + findByInStock(false).size()` returns 20, not
  250.
- **Jackson 3.x packages in Spring Boot 4:** `DataLoader` must import `tools.jackson.core.type.TypeReference`
  and `tools.jackson.databind.ObjectMapper`, not the `com.fasterxml.jackson` packages. Spring Boot
  4.0.6 bundles Jackson 3.x.
- **Kaggle CSV category fragmentation:** Categories are single LC subject headings with internal
  commas (e.g., "Authors, English"). Splitting on commas shreds them into meaningless fragments.
  Regex-based pattern matching is the correct approach.
- **exec:java path resolution:** `exec-maven-plugin` resolves relative paths from the repository
  root, not the module directory. Default paths must be prefixed with the module directory name.

**Tests:** 414 total (363 unit + 51 integration), 0 failures. JaCoCo coverage gate passes at >80%.

---

## 2026-05-14 — Day 3 (continued): Custom Document Mapping Layer

### Session 2: SolrDocumentReader — Bypass SolrJ's DocumentObjectBinder (14:30–15:40)

**Commit:** `2f42b41`

The `getAll` endpoint in the sample app threw `IllegalArgumentException: Can not set String field
to ArrayList` — SolrJ's `DocumentObjectBinder` can't handle the type mismatch when
`LatLonPointSpatialField` with `docValues="true"` returns `ArrayList<Double>` instead of
the `String` that was indexed. Rather than patching the schema or the entity, we built what the
original spring-data-solr always had: a custom document mapping layer that sits between Solr
responses and Java entities.

Designed a single generic interface `SolrDocumentConverter<S, T>` with a `convert()` method, then
two concrete implementations — `SolrDocumentReader<T>` (SolrDocument → entity) and
`SolrDocumentWriter<T>` (entity → SolrInputDocument, stub for now). This replaced the unused
`SolrReadConverter` and `SolrWriteConverter` marker interfaces from the v0.1 converter pipeline
scaffolding.

`SolrDocumentReader` uses reflection to walk `@Field`-annotated fields (including superclass
hierarchy), resolve Solr field names from annotation values, and apply a type coercion chain:
direct assignment → single-element collection unwrap → `ArrayList<Double>` to comma-joined `String`
(the spatial field fix) → multi-valued field to `List`. Also handles `@Score` mapping.

Wired into `SolrTemplate` by replacing all six `response.getBeans(type)` calls with
`mapDocuments(response.getResults(), type)` using the new reader. Removed the `SolrCustomConversions`
field from `SolrTemplate` (it was never applied). Updated all test fixtures across `SolrTemplateTest`
and `MicrometerSolrTemplateTest` to use real `SolrDocument` objects instead of mocking `getBeans()`.

Also produced `SOLR-FIELD-TYPES.md` — a comprehensive reference documenting Java type mappings for
all Solr 9/10 field types, the `DocsStreamer.getValue()` dispatch chain, and the `KNOWN_TYPES` set
that determines whether `toObject()` or `toExternal()` is called. Key finding: spatial types are NOT
in KNOWN_TYPES, which is why the docValues path returns different types than the stored field path.

**Gotchas discovered:**
- SolrJ's `DocumentObjectBinder` performs strict type matching with zero coercion — `ArrayList<Double>`
  to `String` is simply not supported, despite Solr accepting `String` on write and returning
  `ArrayList` on read for the same spatial field
- `DocsStreamer.getValue()` has a two-path dispatch: if the field type is in `KNOWN_TYPES`, it calls
  `toObject()` (typed); otherwise `toExternal()` (String). Spatial types aren't in `KNOWN_TYPES`.
  The docValues code path is entirely separate and can return different Java types
- Non-public inner test classes (`static class TestDocument`) fail with `IllegalAccessException` when
  instantiated via `getDeclaredConstructor().newInstance()` — fixed by making test fixtures `public`
  rather than using `constructor.setAccessible(true)` (SonarQube flagged the reflection approach)
- SolrJ's `@Field` annotation uses `"#default"` as the placeholder for "use the Java field name" — must
  check for both empty string and `"#default"`

**Tests:** 401 total (367 unit + 34 integration), 0 failures. 4 new reader tests + fixture updates
across 4 existing test files.

### Session 3: SolrDocumentWriter — Complete the Mapping Layer (PR #9)

**Commit:** `7a8d812` (merged via PR #9, `ac11caf`)

Closed the write-side gap left by Session 2. `SolrDocumentWriter` mirrors `SolrDocumentReader` — walks
`@Field`-annotated fields (including superclass hierarchy), resolves Solr field names from annotation
values, and converts entity → `SolrInputDocument`. `SolrTemplate.save()` and `saveAll()` now bypass
SolrJ's `DocumentObjectBinder` entirely, using the writer for symmetric round-trip semantics with the
reader.

This completes the custom document mapping layer that was the original `spring-data-solr`'s strongest
feature — and the one most teams missed when forced to fall back to raw SolrJ after archival.

**Tests:** SolrDocumentWriter test class added, existing SolrTemplate save tests adapted. Build green.

### Session 4: Docs Reshuffle, Architecture Diagrams, Dependency Hygiene (later in the day)

**Commits:** `13f8599` (image move), `38b60b7`, `1406a21`, `fc3480e`, `262e987`, `027c24d` (README polish),
plus Dependabot merges `eea738f`, `940e071`, `20eff35`, `009737e`, `2280565`.

Docs housekeeping: moved `DEVLOG.md`, `LIMITATIONS.md`, and `SOLR-FIELD-TYPES.md` under `docs/`,
restructured the README feature list into a table, fixed assorted spelling, moved the title image
into `assets/`. Three README links to the moved docs were stale (`README.md:63`, `:176`, `:177`) —
fixed in this same session.

Added `docs/ARCHITECTURE.md` with four Mermaid diagrams covering the auto-configuration chain, the
repository-call sequence flow, the Spring Boot middleware integration map, and the configuration
surface. Renders inline on GitHub.

Dependabot merges absorbed cleanly: jacoco 0.8.13 → 0.8.14, surefire 3.5.5, compiler-plugin 3.15.0,
`actions/setup-java` → 5, `actions/upload-artifact` → 7, `actions/checkout` → 6.

**Tests:** 401 total (367 unit + 34 integration), unchanged. No production code touched.

---

## 2026-05-17 — Day 4: Declarative Highlighting and Faceting

### Session 1: @Highlight and @Facet Method-Level Annotations (feat/highlight-facet-annotations)

**Branch:** `feat/highlight-facet-annotations`

Implemented two new method-level annotations for the repository layer — `@Highlight` and `@Facet` —
mirroring the pattern established by `@Query`. Repository authors can now declare highlighting and
faceting declaratively on the interface method rather than having to call `SolrTemplate`
imperatively.

**API surface:**

```java
@Highlight(fields = "title", prefix = "<mark>", postfix = "</mark>", snippets = 2)
HighlightPage<Book> findByTitleContaining(String term, Pageable pageable);

@Facet(fields = {"author", "genre"}, queries = {"year:[2000 TO 2010]"}, minCount = 1)
FacetPage<Book> findByTitleContaining(String term, Pageable pageable);

// Both annotations compose with @Query for string-based queries:
@Query("title:?0 AND author:?1")
@Highlight(fields = "title")
HighlightPage<Book> searchBooks(String term, String author, Pageable pageable);
```

**New production files:**
- `Highlight.java` — annotation with `fields`, `fragsize`, `snippets`, `prefix`, `postfix`
- `Facet.java` — annotation with `fields`, `queries`, `minCount`, `limit`
- `HighlightAnnotationAdapter.java` — converts `@Highlight` → `HighlightOptions`
- `FacetAnnotationAdapter.java` — converts `@Facet` → `FacetOptions`
- `Criteria.raw(String)` — new factory method on `Criteria` for embedding pre-built query strings

**Modified production files:**
- `PartTreeSolrQuery` — accepts `Method` in constructor (previously parsed from `QueryMethod`
  which exposes it only package-privately); detects `@Highlight`/`@Facet` before the standard
  page/collection dispatch. Highlight/facet branches both set pageable and the appropriate options
  object before calling the matching template method.
- `StringBasedSolrQuery` — same `Method` constructor pattern; wraps the resolved string query in a
  `SimpleQuery` via `Criteria.raw()` for the highlight/facet paths (those template methods require
  `SimpleQuery`, not raw `SolrQuery`). Also fixes a latent bug: `Pageable` parameters were being
  passed to `ClientUtils.escapeQueryChars` — now skipped in `resolveParameters`.
- `SolrQueryLookupStrategy` — passes the `Method` through to both `RepositoryQuery` implementations.

**Design decision:** Return type (`HighlightPage` / `FacetPage`) is the dispatch signal. The
annotations _require_ the corresponding return type. This prevents silent mismatches where someone
adds `@Highlight` to a method returning `Page<T>` and wonders why snippets are missing — the
highlight path simply won't activate. Clean fail-fast without an exception.

**Gotchas discovered:**
- `QueryMethod.getMethod()` is package-private in Spring Data Commons 4.0.5 despite appearing
  in `javap` output. The `javap` decompiler shows the method regardless of access modifier.
  Solution: pass `Method` explicitly via constructor from `SolrQueryLookupStrategy` which already
  holds it from `resolveQuery(Method method, ...)`.
- PartTree parses method names strictly — test repository methods like `findByTitleContainingFaceted`
  fail with `PropertyReference: No property 'faceted' found`. Test methods must use valid PartTree
  vocabulary even when the dispatch logic ignores the parse result.
- `new HighlightPage<>(List.of(), ...)` infers `HighlightPage<Object>` when used in Mockito stubs
  typed to `HighlightPage<Book>`. Fix: use raw type variable (`HighlightPage emptyPage`) with
  `@SuppressWarnings("rawtypes")` on the test class.

**Tests:** 42 new tests across 4 test classes. 443 total (409 unit + 34 integration), 0 failures.
JaCoCo line coverage gate passes (≥80%).

---

## 2026-05-17 — Day 4: Streaming Expressions Integration Tests

### Session 1: Real-Solr Verification of streaming()/stream() (22:00–23:00)

**Branch:** `test/streaming-integration-tests`

Added `@Nested StreamingExpressions` to `AbstractSolrIntegrationTest` — 5 tests that run against
both Solr 9 and Solr 10 via Testcontainers. In the process, discovered and fixed two bugs in
`SolrTemplate.stream()` that had been masked by the unit tests using mocked responses.

**Tests added:**
- `searchExpressionReturnsIndexedDocumentsExcludingEofTuple` — `search()` fluent builder, verifies
  3 indexed docs come back in sort order
- `streamResultDoesNotContainEofTuple` — asserts no map contains an `EOF` key
- `streamResultContainsRequestedFields` — verifies `fl` parameter is respected, field values correct
- `rawExpressionViaOfReturnsResults` — `StreamingExpression.of(raw)` path, broader assertion
- `emptyCollectionReturnsEmptyList` — zero-result baseline

**Bugs discovered and fixed:**

1. **`GenericSolrRequest` was routing to `/solr/stream` instead of `/solr/books/stream`**.
   `GenericSolrRequest.requiresCollection` defaults to `false`. Without `.setRequiresCollection(true)`,
   `HttpJdkSolrClient.buildRequestUrl()` omits the collection segment and the root-level `/stream`
   servlet responds with `HTTP 405 POST not supported`. Fix: chain `.setRequiresCollection(true)` on
   the `GenericSolrRequest` construction.

2. **`parseTuples()` failed silently for JavaBin wire responses**. The unit tests mock
   `solrClient.request()` to return `NamedList` responses, so `parseTuples()` was written and tested
   against `NamedList`. In the real JavaBin protocol, the `/stream` handler encodes:
   - `result-set` value as `MAP_ENTRY_ITER` → `Map<Object,Object>` (NOT `NamedList`)
   - individual doc tuples also as `MAP_ENTRY_ITER` → `Map<Object,Object>`
   - the `docs` container as `ITERATOR` → `List<Object>` (this part was handled correctly)
   Both the `resultSet` and `doc` branches needed `instanceof Map<?,?>` pattern matching alongside
   the existing `NamedList<?>` branches.

**On `/stream` handler availability:**

The task notes flagged this as a possible blocker. Investigation:
- Standalone Solr (mode `"std"`) DOES have the `/stream` endpoint registered, but executing any
  `search()` expression throws `NullPointerException: ZooKeeper host cannot be null!` — streaming
  expressions require ZooKeeper to resolve collection shards.
- `SolrContainer` in Testcontainers 2.0.5 defaults `zookeeper = true`, so containers started with
  `.withCollection(name)` run in embedded-ZK SolrCloud mode. The existing tests already relied on
  this. The `/stream` handler works out of the box — **no custom config, no mounted schema required**.
- Confirmed on both `solr:9` and `solr:10` Docker images.

**SolrJ 10 JavaBin format note:**
The JavaBin `ITERATOR` (tag 14) is used for the docs container, `MAP_ENTRY_ITER` (tag 17) for
each tuple. Both decode through `JavaBinCodec.readIterator()` → `ArrayList` and `readMapIter()` →
`LinkedHashMap` respectively. The original `parseTuples()` checked only for `NamedList` — the Map
branch is the actual wire format.

**Tests:** 419 total (377 unit + 42 integration — 5 new streaming tests × 2 Solr versions = 10 new
integration tests), 0 failures. JaCoCo coverage gate passes at >80%.

---

## 2026-05-17 — Day 4: Geospatial Integration Tests

### Session 1: Testcontainers Verification of near() and within() (22:00–22:30)

**Branch:** `test/geo-integration-tests`

Added a `GeoSpatial` `@Nested` class to `AbstractSolrIntegrationTest` — it runs automatically
against both Solr 9 and Solr 10 via the existing dual-version subclass pattern. Four tests cover
the full geospatial query surface:

- `nearQueryReturnsDocumentsWithinRadiusInKilometres` — `{!geofilt}` finds London within 100 km,
  excludes Edinburgh (~534 km) and Tokyo
- `nearQueryReturnsDocumentsWithinRadiusInMiles` — 62 miles (~99.8 km) exercises the
  `GeoDistance.miles()` → km conversion path in `GeoDistance.toKilometers()`
- `withinQueryReturnsBoundingBoxResults` — `{!bbox}` finds Paris within 200 km bounding box,
  excludes London (~340 km) and Tokyo
- `documentOutsideRadiusIsExcluded` — tight 10 km circle around London, asserts only 1 result

**Schema API technique:** The `_default` configset ships with the `location` field *type*
(`LatLonPointSpatialField`) pre-registered. A `@BeforeEach` in the nested class posts to
`/solr/books/schema` to add the concrete `location` *field* before any indexing. HTTP 400
("field already exists") is treated as a no-op, making the call idempotent across tests.

**Gotchas discovered:**

- `_default` configset: the field TYPE `location` (`LatLonPointSpatialField`) is already
  registered; only the field definition itself is missing. The Schema API `add-field` command
  with `{"name":"location","type":"location"}` is all that's needed.
- HTTP 400 from Schema API when field already exists is normal — the field is still usable.
  Accept both 200 (first add) and 400 (idempotent re-add) as success states.
- `SolrDocumentReader` correctly handles the `ArrayList<Double>` → comma-joined `String`
  coercion for `LatLonPointSpatialField` values read back from Solr (docValues path). No
  additional mapping code was needed — the existing coercion chain handles it.
- `Criteria.near()` and `Criteria.within()` render raw predicates without a `field:` prefix,
  which is correct — the field name is embedded in the `{!geofilt sfield=...}` syntax itself.

**Tests:** 417 total (367 unit + 50 integration), 0 failures. JaCoCo coverage gate passes.

---

## What's Next (post Day 4)

- [ ] Publish to Maven Central
- [x] Converter application during read (SolrDocumentReader wired into SolrTemplate, Day 3 Session 2)
- [x] Converter application during write (SolrDocumentWriter, Day 3 Session 3, PR #9)
- [x] `@Highlight` and `@Facet` method-level annotations for repository methods (Day 4, feat/highlight-facet-annotations)
- [x] Geospatial integration tests with Testcontainers (PR #10 — `test/geo-integration-tests`)
- [x] Streaming expressions integration tests (Day 4, `test/streaming-integration-tests` — 5 tests, 10 IT runs)

---

## 2026-06-01 — Day 5: Code Review, Issue Triage, and Bug Sprint

### Session 1: Dual Code Review and Issue Creation (Morning)

**Commits:** `eb3d00d` → `16a94d1`

Started the session with a comprehensive review of the codebase, running two independent reviewer
agents modelled on Josh Long and Rod Johnson. Both reviewers read the full autoconfigure module
independently and reported back. The overlap in their findings gave high confidence on the critical
issues; the divergence surfaced a few things only one of them caught.

The review produced 20 GitHub issues (#32–#51), grouped as:

- **Critical bugs:** converter pipeline broken, `findAll()` truncation, `Criteria` range escaping,
  `queryForPage` ignoring `Pageable`
- **Major bugs:** per-call reader/writer instantiation, hardcoded `String.class` ID type,
  hardcoded `"solrTemplate"` bean name
- **Design/refactor:** bifurcated mapping model, `SolrDocumentResolver` as static utility,
  `@SolrDocument` naming collision with SolrJ's own class
- **Minor:** `getSolrClient()` public, missing Micrometer coverage, `spring-tx` dead dependency

The most structurally significant finding from both reviewers independently: `SolrCustomConversions`
is wired as a Spring bean and annotated `@ConditionalOnMissingBean`, implying users can override it
— but `SolrDocumentReader` and `SolrDocumentWriter` never consult it. It is a broken public
contract, not just a missing feature.

### Session 2: First Bug Sprint — Four Parallel Agents (Morning)

**Commits:** `04f3f3d`, `1c3efb2`, `e4a771f` (later revised), `1c508ac` → PRs #52–#55

Dispatched four developer agents in parallel using git worktree isolation, each targeting a
different bug. All four completed BUILD SUCCESS before pushing:

- **#34 → PR #52** — `Criteria.between()`, `lessThan()`, `lessThanEqual()`, `greaterThan()`,
  `greaterThanEqual()` now call `escape()` on bound values, consistent with every other predicate
  method. Ten new tests, red-green cycle confirmed. This was also a minor security fix — unescaped
  user-controlled strings in range queries are a Solr query injection vector.
- **#33 → PR #53** — `findAll()` and `findAll(Sort)` initially fixed with `Integer.MAX_VALUE` rows.
  Revised after review discussion (see Session 3).
- **#35 → PR #54** — `queryForPage(SimpleQuery, Class<T>, Pageable)` was silently discarding the
  `Pageable` parameter. Single line fix: `query.setPageable(pageable)` before delegation.
- **#51 → PR #55** — The `WithoutCollection` health indicator unit test was testing the error path
  (connection refused → `DOWN`) while claiming to test the happy path. Added
  `reportsUpWhenAdminEndpointReturnsValidResponse` mocking `solrClient.request(...)` directly.
  Also corrected the misleading test name on the existing error-path test.

### Session 3: findAll() Research and Resolution

**Commits:** `0fe8900`, `7450bbe`

PR #53 prompted a discussion about whether `Integer.MAX_VALUE` was the right fix. Ran a three-agent
research swarm to check what Spring Data MongoDB and Elasticsearch do:

- **MongoDB** (`SimpleMongoRepository`): fully unbounded, no row limit, materialises entire
  collection into `List<T>`. Works until the heap runs out.
- **Elasticsearch** (`SimpleElasticsearchRepository`): calls `count()` then issues
  `PageRequest.of(0, totalCount)`. Throws `"Result window is too large"` for collections over
  Elasticsearch's `index.max_result_window` default of 10,000 — identical to Solr's constraint.
- **Spring Data Commons** `CrudRepository`: no official warning in the Javadoc whatsoever.
  "Returns all instances of the type." Community acknowledges it as an anti-pattern (Vlad Mihalcea
  has an article titled exactly that); the framework has never acted on it.

Resolution: a named constant `DEFAULT_MAX_ROWS = 10_000` matching Solr's own default
`maxResultWindow`. The number is principled (it's Solr's actual limit, not an arbitrary guess),
and the Javadoc explains the constraint and points to `findAll(Pageable)` and `queryWithCursor`
for large collections. Research report saved to `docs/research/findall-behaviour-spring-data-stores-2026-05-29.md`.

**Gotchas discovered:**
- `Integer.MAX_VALUE` rows against a Solr collection exceeding `maxResultWindow` (default 10,000)
  throws a `SolrException`. You are trading silent truncation for a loud failure.
- The original `spring-data-solr` (spring-attic) had the same `findAll()` bug — no `setRows()`
  at all. We didn't inherit it; we independently reproduced it.
- Cursor-based `findAll()` via `queryWithCursor()` would be architecturally correct but still
  materialises everything in memory before returning — not safe for million-document collections
  regardless of implementation.

### Session 4: Second Bug Sprint — Four More Parallel Agents (Afternoon)

**Commits:** `9570261`, `a72787f`, `1148fa3`, `a60c9a2` → PRs #56–#59

Second wave of four agents, targeting the remaining structural bugs:

- **#36 → PR #57** — `SolrTemplate` was instantiating `SolrDocumentWriter` and
  `SolrDocumentReader` on every `save`, `saveAll`, and `mapDocuments` call. Key design finding:
  `SolrDocumentWriter` is completely stateless — it derives the entity class from the argument at
  `convert()` time, not from a constructor parameter. A single `private final` shared instance is
  sufficient. `SolrDocumentReader<T>` takes `Class<T>` at construction and must be cached per type
  via `ConcurrentHashMap`, matching the existing `SolrFieldNameResolver` pattern.

- **#48 → PR #56** — `SolrFieldNameResolver` static `ConcurrentHashMap<Class<?>, ...>` replaced
  with `Collections.synchronizedMap(new WeakHashMap<>())`. The existing `clearCache()` method in
  test `@AfterEach` was a tell that this risk was previously recognised. The inline comment
  explaining the rationale was moved to a class-level Javadoc block after a review comment on the
  PR — inline comments on field declarations are the wrong level for this explanation.

- **#38 → PR #58** — `SolrRepositoryConfigurationExtension` had two `postProcess` overloads that
  diverged: the annotation-specific one read `solrTemplateRef`; the base one hardcoded
  `"solrTemplate"`. Fixed with `source.getAttribute("solrTemplateRef")` and a blank-safe fallback.
  New test class (6 tests) created from scratch, including the red test that proved the bug before
  the fix was applied.

- **#37 → PR #59** — `SolrRepository<T, ID>` removed the `ID` type parameter entirely. The
  unchecked `(Class<ID>) String.class` cast at construction time and `id.toString()` coercions
  throughout `SimpleSolrRepository` were symptoms of an implicit assumption that was never
  documented. `SolrRepository<T>` now extends `PagingAndSortingRepository<T, String>` and
  `CrudRepository<T, String>` directly. `SolrEntityInformation` similarly simplified.
  `SolrRepositoryFactoryBeanSupport` continues to work because Spring Data's reflection-based
  metadata discovery correctly infers `String` from the parameterised supertypes.

### Session 5: PR Hygiene

**Commits:** `16a94d1`

Updated all open PR descriptions to plain professional English — earlier descriptions had been
generated with themed creative writing (crime noir, horror, workplace comedy, etc.) which was not
appropriate. Updated the PR template to note that CI handles build and test automatically, removing
the need for a `## Test Plan` section in individual PRs.

**Tests:** 448 total (430 unit + 18 integration — integration tests skipped without Docker),
0 failures.

### Session 6: Repo Rename, Criteria.matchAll, Cleanup (Evening)

**Commits:** `1686de8`, `f2a71a8`, `38849de`, `ab3e000`

GitHub repo renamed from `spring-data-solr-lazarus` to `spring-data-solr`. The Lazarus
identity stays in the local working directory name and in the project's origin story —
it just stops being the public face. Pushed `chore:` commit updating every reference in
the codebase (POMs, README, docs) to the new repo URL.

- **#42 → PR #60** — `Criteria.matchAll()` factory method. Previous workaround
  `Criteria.where("*").expression("*")` accidentally happened to render `*:*` but only by
  coincidence of `Criteria`'s rendering rules. New method is explicit, documented, and the
  intent reads at the callsite.
- **#46 → PR #62** — Removed `spring-tx` from `solr-spring-boot-starter` POM. No transaction
  manager integration exists; the dependency was inherited from somewhere and noise on
  consumer classpaths.
- `CLAUDE.md` overhauled for accuracy after Day 5 churn — module structure, gotchas section
  rewritten, dev log convention noted.

### Session 7: Third Bug Sprint — Four Parallel Agents (Evening)

**Commits:** `f1769c4`, `e15db74`, `0de384d`, `ba20bd1` → PRs #63, #64, #65, #66

Repeated the parallel-worker pattern with isolated worktrees. All four landed cleanly:

- **#44 → PR #63** — `MicrometerSolrTemplate` had Timer instrumentation on the hot CRUD
  methods but four operations (`savePartialUpdate`, `commit`, `queryWithCursor`, and one
  more) were silently bypassing the meter registry. Added instrumentation matching the
  existing pattern. The fix would have been one line per method had the previous author
  reached for a higher-level abstraction — telegraphs the Observation API migration that
  came later.
- **#43 → PR #65** — `SolrTemplate.getSolrClient()` made package-private. Public access
  invited consumers to bypass the template and lose the commit-mode contract.
- **#49 → PR #66** — Default page size for `PartTreeSolrQuery` made configurable via
  `spring.solr.default-page-size` (was a hardcoded `10`). Tests use `@ParameterizedTest`
  to cover boundaries.
- **#27 → PR #64** — Javadoc on the public mapping API and template layer. Not glamorous,
  but it's the surface consumers will read.

### Session 8: Named @Query Params, SolrCloud Integration, Common Questions (Late)

**Commits:** `7f33bb1`, `e4bebb0` → `6d985c5`, `bf1a3d8` → `9f17377` → PRs #67, #68, #69

Three threads ran roughly in parallel; one of them produced the day's nastiest gotcha.

- **#41 → PR #69** — Named parameters in `@Query`. Previously only positional `?0` `?1`
  substitution; now `:title :author` resolved against `@Param`-annotated method args.
  `StringBasedSolrQuery` regex updated to recognise both forms. Mixing positional and
  named in the same query is rejected at parse time with a clear error.
- **#22 → PR #68** — SolrCloud integration test using Testcontainers' embedded
  ZooKeeper. The unit tests had verified the bean wiring but nothing exercised an actual
  cluster. This produced one architectural fix (`cc095af`): the autoconfig was previously
  passing solr URLs to `CloudSolrClient` instead of using the ZooKeeper provider builder
  — wrong by design, but only visible when you actually point it at ZooKeeper.
- **#20 → PR #67** — Schema management section moved out of README into a new
  `docs/COMMON-QUESTIONS.md`. The README intro voice survives; the dry FAQ-style content
  lives elsewhere.

**Gotchas discovered:**
- `SolrContainer` in Testcontainers doesn't pin ports by default. `CloudSolrClient` needs
  to be able to reach the Solr node *and* the embedded ZooKeeper, and they discover each
  other via the address ZooKeeper registers — so if the host-side port doesn't match the
  container-side port, the client gets a routing error like
  `No live SolrServers available to handle this request`. Pinning the SolrContainer port
  with a `FixedHostPortGenericContainer`-style binding fixed it.
- `CloudSolrClient` builder API has two paths: `withSolrUrl(...)` and the ZK provider
  builder. They look interchangeable in unit tests because nothing actually connects. In
  practice, `withSolrUrl` constructs a client that bypasses ZooKeeper-driven routing —
  which defeats half the point of using SolrCloud. The autoconfig had to be reworked to
  always go through the ZK builder when `spring.solr.cloud` is configured.

### Session 9: Observation API Upgrade (Late)

**Commits:** `0ffedbd`, `9f89087` → PR #70

Replaced the hand-rolled `Timer` instrumentation in `MicrometerSolrTemplate` with the
Micrometer Observation API. Same metrics now flow through `ObservationRegistry`, which
means distributed tracing context (OpenTelemetry/Zipkin/Brave) is propagated automatically
when consumers wire an exporter. The migration was test-heavy — 250+ lines of test
changes — because the assertion shape moves from "Timer exists with these tags" to
"Observation was started, stopped, has these key-values, and produced these meters".

The autoconfig now creates `MicrometerSolrTemplate` only when an `ObservationRegistry`
bean is present — same wiring style as the rest of Spring Boot 4's observation
auto-config.

**Gotchas discovered:**
- The Observation API records a `Timer` as a side-effect, but the timer's *name* depends
  on the `ObservationConvention` registered for the observation. Default convention
  prefixes with the observation name only. Old test assertions checking
  `Timer.builder("solr.template.save")` had to migrate to the new naming and rely on the
  observation registry rather than the meter registry for setup.
- `Observation.start().stop()` and `Observation.observe(Runnable)` have subtly different
  exception semantics. The `observe` overloads catch and re-throw, marking the observation
  as errored; the explicit `start/stop` form doesn't unless you call `.error(throwable)`.
  Got this wrong in one method, caught by an integration test that asserted error tags.

### Session 10: Fifth Bug Sprint — Four Parallel Agents on the Mapping Layer (Late Evening)

**Commits:** in flight — workers running in isolated worktrees

Five issues remain in the mapping/converter layer, four of which are architecturally
interrelated:

- **#32** — `SolrCustomConversions` pipeline wired but ignored by reader/writer
- **#39** — Bifurcated mapping model (two parallel reflection paths)
- **#40** — `SolrDocumentResolver` should be an injectable bean
- **#50** — `SolrPersistentEntity.getCollection()` doesn't resolve `${placeholder}` names

Dispatched four developer agents in parallel, each in an isolated git worktree, each
charged with TDD-first implementation and raising its own PR with `Closes #N`. Merge
conflicts at PR review time are expected and accepted — they all touch the same files,
and the human will resolve at merge. The alternative (sequential implementation) loses
the speedup the worktree pattern is meant to provide.

**Tests:** 529 total, 0 failures (up from 448 at end of Session 5 — net +81 tests across
sessions 6–9, all green).

---

## What's Next

- [ ] **#32** — Wire `SolrCustomConversions` through to `SolrDocumentReader`/`Writer` (blocked on #39)
- [ ] **#39** — Unify bifurcated mapping model: `SolrMappingContext` scaffolding exists but
       `SolrDocumentReader`/`Writer` bypass it entirely; two parallel reflection paths will drift
- [ ] **#40** — Make `SolrDocumentResolver` an injectable Spring bean; currently a static utility
       with optional `Environment` parameter causing silent placeholder failures in repository layer
- [ ] **#41** — Named parameter support in `@Query` (positional `?N` substitution is fragile)
- [ ] **#42** — `Criteria.matchAll()` factory method
- [ ] **#43** — `SolrTemplate.getSolrClient()` should not be public
- [ ] **#44** — Complete Micrometer instrumentation (`savePartialUpdate`, `commit`, `queryWithCursor`)
- [ ] **#45** — Upgrade `MicrometerSolrTemplate` to `ObservationRegistry` for distributed tracing
- [ ] **#46** — Remove `spring-tx` from starter POM (no transaction manager integration exists)
- [x] **#47** — Rename `@SolrDocument` → `@SolrEntity` to eliminate collision with `org.apache.solr.common.SolrDocument`
- [ ] **#49** — `PartTreeSolrQuery` default pagination — document or make configurable
- [ ] **#50** — `SolrPersistentEntity.getCollection()` doesn't resolve `${placeholder}` names
- [ ] **#27** — Javadoc on public mapping API
- [ ] **#23** — Publish to Maven Central
