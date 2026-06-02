# Spring Data Solr Lazarus — Dev Log

A modern Spring Boot starter for Apache Solr 10, resurrecting the archived `spring-data-solr` project.

---

## 2026-06-01 — Day 1: Code Review, Issue Triage, and Bug Sprint

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
- `CLAUDE.md` overhauled for accuracy after Day 1 churn — module structure, gotchas section
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
