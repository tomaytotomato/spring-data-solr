# Common Questions

A growing list of things people ask. Each section is self-contained — read what you need
and skip the rest.

## Why can't I use this library to manage a Solr schema?

Because that's not what it's for. This library only cares about indexing documents and
running queries once a collection already exists. Setting up a collection — deciding which
fields exist, what types they are, whether you want a managed schema or schema-less mode —
is the responsibility of the Solr administrator (or your deployment pipeline).

The boundary is intentional. Schemas are a deployment concern, and there are already good
tools for that — `solrctl`, the Schema API, configsets, your CI/CD pipeline. This library
focuses on the runtime path: documents in, documents out.

## What the library has been tested against

**Integration tests** (Solr 9 and Solr 10 via Testcontainers) run against the `_default`
configset, which ships with Solr and uses a **managed schema** with dynamic field patterns
(`*_s`, `*_i`, `*_t`, etc.). The Testcontainers `SolrContainer` creates the collection
on startup; the tests never touch the schema directly, except the geospatial suite — which
calls the Schema API to add a `location` field. That works because managed-schema Solr
supports runtime schema changes.

**The sample app** (Docker Compose) mounts a custom `managed-schema.xml` into the
`_default` configset. It defines explicit dynamic fields for the `books` collection — the
same naming conventions (`*_s`, `*_i`) used in the integration tests.

In practice, both setups behave like **schema-on-write with dynamic fields**: you name your
Java fields with the right suffix in your `@Field` annotations and Solr resolves the type
automatically. No Schema API calls from application code, no field registration step.

## What this means for you

- **Managed schema** (`managed-schema.xml`, editable via Schema API): works fine. Define
  your fields in the schema before indexing, or use dynamic fields and lean on the suffix
  conventions.
- **Schema-less mode** (Solr's `_default` configset with `schemaFactory` set to
  `ManagedIndexSchemaFactory` and field guessing enabled): also works. Solr infers field
  types from the first document indexed; this library just sends the documents.
- **Classic `schema.xml`** (read-only, `ClassicIndexSchemaFactory`): works as long as
  the fields your entity uses are defined in the schema. No library changes needed.

If you need to manage schemas programmatically — creating collections, adding fields via
the Schema API, provisioning configsets — that is out of scope here. Raise a separate
issue if there is a concrete use case; it may warrant a companion module rather than
expanding this one.

## Useful Solr references

- [Schema Design Guide](https://solr.apache.org/guide/solr/latest/indexing-guide/schema-elements.html)
- [Managed Schema / Schema API](https://solr.apache.org/guide/solr/latest/configuration-guide/schema-factory.html)
- [Schema-less Mode](https://solr.apache.org/guide/solr/latest/configuration-guide/schemaless-mode.html)
- [Dynamic Fields](https://solr.apache.org/guide/solr/latest/indexing-guide/dynamic-fields.html)
- [Collections API](https://solr.apache.org/guide/solr/latest/deployment-guide/collections-api.html)

## How do I configure observability (metrics and tracing)?

When this starter detects an `ObservationRegistry` bean on the classpath, it swaps the
plain `SolrTemplate` for `MicrometerSolrTemplate` — a subclass that wraps every operation
in a Micrometer `Observation`. You get both timing metrics and distributed tracing spans
through the same API, without writing any instrumentation code.

If you're already pulling in `spring-boot-starter-actuator`, Boot 4 auto-configures an
`ObservationRegistry` for you and this kicks in automatically. No code changes, no extra
properties.

### What you get

Every operation that goes through `SolrTemplate` records an observation with:

| Field | Value |
|-------|-------|
| **Name** | `solr.operation` |
| **`operation` tag** (low cardinality) | `save`, `saveAll`, `query`, `queryForPage`, `queryForHighlightPage`, `queryForFacetPage`, `count`, `deleteById`, `deleteByQuery`, `partial-update`, `commit`, `soft-commit`, `cursor-query` |
| **`collection` tag** (low cardinality) | The Solr collection the operation targeted |

If a metrics handler is registered (e.g. via `MeterRegistry`), you get a Timer named
`solr.operation` tagged by `operation` and `collection`. If a tracing handler is
registered (e.g. `micrometer-tracing-bridge-otel`), you get a span per operation,
linked into your existing trace context.

### Disabling it

There is no `spring.solr.observability.enabled=false` flag — observability follows the
standard Spring Boot conditional model. Pick whichever of these fits:

**Don't include an `ObservationRegistry`.** The instrumentation only activates when a
bean of that type exists. If you're not using actuator and haven't declared one yourself,
nothing is instrumented and the plain `SolrTemplate` is used.

**Define your own `SolrTemplate` bean.** Anything you declare wins over the autoconfigured
one — the `MicrometerSolrConfiguration` is gated on `@ConditionalOnMissingBean(SolrTemplate.class)`.

```java
@Bean
SolrTemplate solrTemplate(SolrClient client, SolrProperties props, Environment env) {
  return new SolrTemplate(client, props.getCommitMode(), env);
}
```

**Suppress the metric or span at the registry level.** Use Micrometer's
`ObservationPredicate` or `MeterFilter` to mute `solr.operation` without touching
this library:

```java
@Bean
ObservationPredicate noSolrObservations() {
  return (name, context) -> !"solr.operation".equals(name);
}
```

### Tweaking it

The observation name and tag set are fixed by design — they need to be stable for
dashboards and alerts. If you want to add extra tags (request id, user id, tenant),
register an `ObservationFilter` and Boot will apply it automatically:

```java
@Bean
ObservationFilter solrTenantTagFilter() {
  return context -> {
    if ("solr.operation".equals(context.getName())) {
      context.addLowCardinalityKeyValue(KeyValue.of("tenant", currentTenant()));
    }
    return context;
  };
}
```

For per-handler customisation (different metric name, different span attributes),
register a custom `ObservationHandler<Observation.Context>` bean. The Micrometer
docs have the full guide:
[Observation handlers](https://micrometer.io/docs/observation#_observationhandler).

### Distributed tracing

Add a tracing bridge and Boot wires the rest:

```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

Configure your collector via `management.otlp.tracing.endpoint` and every `SolrTemplate`
call becomes a span — with the same `operation` and `collection` tags as attributes —
nested under whatever request span is active.

### Why it's not configurable via `spring.solr.*`

The original draft of this feature exposed a property like
`spring.solr.observability.enabled`. We dropped it because every other observability-aware
Boot starter follows the same convention: instrumentation is on when an
`ObservationRegistry` is present, off when it isn't. Adding a project-specific flag would
just be a less standard way of doing the same thing.
