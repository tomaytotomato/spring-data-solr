# Common Questions (FAQs)

## Does this support Spring Reactive or Webflux?

No, however you could add your own reactive wrappers of your SolrRepositories.

## Can I manage Solr schemas with this or use Solr admin features?

No, this is purely for standard Create, Read, Update and Delete (CRUD) operations that you
find with any other Spring Data library.

Solr configs and `managed-schema.xml` is more a deployment or infrastructure concern for SRE or
Solr administrators.

## What versions of Solr does this support?

- Solr 9
- Solr 10


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

| Field                                  | Value                                                                                                                                                                                       |
|----------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Name**                               | `solr.operation`                                                                                                                                                                            |
| **`operation` tag** (low cardinality)  | `save`, `saveAll`, `query`, `queryForPage`, `queryForHighlightPage`, `queryForFacetPage`, `count`, `deleteById`, `deleteByQuery`, `partial-update`, `commit`, `soft-commit`, `cursor-query` |
| **`collection` tag** (low cardinality) | The Solr collection the operation targeted                                                                                                                                                  |

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

## Was this vibe coded?

If you mean was this project created from a basic prompt like: 

> "Claude build a new Spring Boot Solr starter library"

Then no!

Instead this project was vibed from a longing for a library and its historical functionality, this was then combined with 
looking at other Spring Data projects and seeing how we could reforge Spring Solr Data in the latest Spring Framework and Spring Boot versions.

## Useful Solr references

- [Schema Design Guide](https://solr.apache.org/guide/solr/latest/indexing-guide/schema-elements.html)
- [Managed Schema / Schema API](https://solr.apache.org/guide/solr/latest/configuration-guide/schema-factory.html)
- [Schema-less Mode](https://solr.apache.org/guide/solr/latest/configuration-guide/schemaless-mode.html)
- [Dynamic Fields](https://solr.apache.org/guide/solr/latest/indexing-guide/dynamic-fields.html)
- [Collections API](https://solr.apache.org/guide/solr/latest/deployment-guide/collections-api.html)