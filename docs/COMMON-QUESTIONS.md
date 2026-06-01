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
