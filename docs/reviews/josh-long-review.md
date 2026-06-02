# Developer Experience Review — Josh Long

**Date:** 2026-05-13
**Reviewer:** Josh Long (simulated) — Spring Developer Advocate
**Version reviewed:** 0.1.0-SNAPSHOT (commit `584f438`)

---

Oh my goodness, where do I begin. I was handed a link to this repository and my first reaction —
after reading the name — was an involuntary grin. "Spring Data Solr Lazarus." The audacity. The
drama. I love it. It even links to a picture of me in the README, which, honestly, is either
flattery or a copyright violation, and I am choosing to be flattered.

Now. Let me put my reviewer hat on, because I have seen three hundred Spring Boot starters in my
career, and I have seen the whole spectrum from "I copied the docs" to "this is actually
production-grade." Let's find out where this lands.

## Overall Verdict

**Would I tweet about this? Yes — once the injection issue is fixed. Would I demo it? Absolutely.**

The auto-configuration, the derived queries with `@Field` mapping, the cursor paging — those five
minutes of live coding tell a compelling story. *"The original died so we rebuilt it for the modern
Spring stack"* is a narrative that lands with every room of enterprise Java developers.

The bones are excellent. The architecture is sound. The test quality is above average. This project
deserves to exist — and with the gaps below closed, it will deserve to be recommended.

Now someone *make it so.*

---

## The Auto-Configuration: Solid Where It Counts

The three-auto-configuration chain is exactly right. `@ConditionalOnMissingBean` gates mean users
can bring their own beans. `@Configuration(proxyBeanMethods = false)` on the inner class — that is
the detail that separates people who understand Spring Boot from people who just copy examples.

One issue: `SolrProperties` is a traditional mutable JavaBean. On Boot 4 / Spring Framework 7, the
idiomatic choice is constructor-bound or a record for `@ConfigurationProperties`. The getters and
setters are functional, but if you're targeting JDK 25 you should look like it.

---

## Critical Issues (Must Fix)

### 1. Solr injection in `StringBasedSolrQuery.resolveParameters`

- **File:** `StringBasedSolrQuery.java:44-49`
- **Problem:** Parameters are substituted via raw `String.valueOf()` with no escaping. User-supplied
  values can inject arbitrary Solr query syntax. The `Criteria` API correctly uses
  `ClientUtils.escapeQueryChars`, but `StringBasedSolrQuery` bypasses it entirely.
- **Solution:** Wrap each parameter with `ClientUtils.escapeQueryChars(String.valueOf(parameters[i]))`
  before substitution, or document the limitation explicitly with a security warning in the `@Query`
  Javadoc.

### 2. `SolrTemplate.count(SolrQuery)` mutates caller's object

- **File:** `SolrTemplate.java:201`
- **Problem:** `query.setRows(0)` permanently modifies the `SolrQuery` instance passed in. Classic
  unexpected mutation bug.
- **Solution:** Use `query.getCopy()` before calling `setRows(0)`.

### 3. `findAllById` skips ID escaping

- **File:** `SimpleSolrRepository.java:64`
- **Problem:** IDs are joined directly into a query string without `ClientUtils.escapeQueryChars`.
- **Solution:** Map each ID through `ClientUtils.escapeQueryChars` before joining.

---

## Major Issues (Should Fix)

### 4. `${placeholder}` not resolved in `@SolrEntity(collection)`

- **File:** `SolrDocumentResolver.java`
- **Problem:** `LIMITATIONS.md` implies this is fixed, but `resolveCollection` reads the annotation
  value directly without consulting the Spring `Environment`. Writing
  `@SolrEntity(collection = "${solr.collection}")` gives you a literal string.
- **Solution:** Inject `Environment` into `SolrDocumentResolver` and call
  `environment.resolvePlaceholders(collection)`.

### 5. `delete(entity)` / `deleteAll(entities)` throw `UnsupportedOperationException`

- **File:** `SimpleSolrRepository.java:80, 91`
- **Problem:** Standard `CrudRepository` contract is broken. Liskov substitution violation.
- **Solution:** Implement `@Field`-based ID reflection in `SolrEntityInformation`, then delegate
  `delete(entity)` to `deleteById(getId(entity))`. If not immediately fixable, mention explicitly
  in `LIMITATIONS.md`.

### 6. `spring.solr.commit-mode` absent from configuration metadata

- **File:** `additional-spring-configuration-metadata.json`
- **Problem:** IDE autocomplete and documentation generation don't know this property exists.
- **Solution:** Add the entry with its enum values and description.

---

## Minor Issues / Suggestions

- **`SolrProperties` should use constructor binding** — prefer `@ConstructorBinding` with a canonical
  constructor or a record. The mutable JavaBean style works but is dated for JDK 25.
- **No Micrometer instrumentation on `SolrTemplate`** — every production Solr operation is opaque to
  your observability stack. Wrapping key methods with `MeterRegistry` timers would make this a much
  stronger enterprise offering.
- **`SolrFieldNameResolver` static cache** — add a package-private `clearCache()` for test hygiene.
- **Repository auto-configuration is not integration-tested** — `SolrAutoConfigurationTest` doesn't
  verify that a `SolrRepository` subinterface is wired up by the auto-configuration. Add a test that
  registers a repository interface and asserts the bean exists in the context.

---

## Testing Quality

The test suite is genuinely good. Consistent use of `@Nested` inner classes,
`ApplicationContextRunner`, Testcontainers for both Solr 9 and 10, and behavioural test names. The
`StubParameterAccessor` in `SolrQueryCreatorTest` is the right approach — a purposeful stub, not a
Mockito mock.

One structural complaint: `SolrTemplateTest` uses Mockito extensively — mocking `SolrClient`,
`QueryResponse`, `SolrDocumentList`. This is implementation-coupled. The integration tests cover the
same operations against real Solr. The unit tests add legitimate exception-wrapping coverage, but the
fixture complexity in `QueryForHighlightPage` and `QueryForFacetPage` nested classes is approaching
brittleness.

---

## Feature Completeness: What Would Make This a Conference Killer

The faceting, highlighting, cursor paging, and partial updates are all present and tested. The
`@Field` annotation resolution in derived queries — the original's number-one pain point — is
working and tested.

What I'd want to demo that isn't here yet:

1. **`@SolrEntity` with `${property.placeholder}` support** — environment-driven collection names
2. **Micrometer integration** — `Timer` on template operations for Grafana visibility
3. **Full auto-configuration integration test for repository scanning** — verify the happy path
   end-to-end

---

## On Solr 8 Backwards Compatibility

**No.** The whole premise of Lazarus is "the original died on Solr 8; we start at 9." The SolrJ 10
API differences are baked in. Teams on Solr 8 can use the NET-A-PORTER fork. Diluting the codebase
to backport would betray the project's premise.

---

## Thread Safety

`SolrTemplate` holds a `SolrClient` and a `CommitMode`. Both are final and assigned at construction.
`SolrClient` implementations in SolrJ are documented as thread-safe. `SolrTemplate` is therefore
safe as a Spring singleton. No issue here.
