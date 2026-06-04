# Architecture Diagrams

Visual reference for how `spring-data-solr` integrates with Spring Boot's
auto-configuration, Actuator, and Spring Data infrastructure. All diagrams are
Mermaid — render them in GitHub, IntelliJ, or any Markdown viewer with Mermaid
support.

---

## 1. Spring Boot Auto-Configuration Chain

How the starter wires itself into a Spring Boot application at startup. The
three `@AutoConfiguration` classes are registered via
`AutoConfiguration.imports` and pulled in by `SpringFactoriesLoader` during the
boot lifecycle.

```mermaid
flowchart TD
  subgraph BootStartup["Spring Boot Startup"]
    SBA["SpringApplication.run()"]
    AC["AutoConfigurationImportSelector"]
    SFL["SpringFactoriesLoader<br/>reads<br/>AutoConfiguration.imports"]
    SBA --> AC --> SFL
  end

  subgraph Starter["solr-spring-boot-starter"]
    SP["SolrProperties<br/>(spring.solr.*)"]
    SAC["SolrAutoConfiguration"]
    SHAC["SolrHealthAutoConfiguration<br/>@AutoConfigureAfter(SolrAutoConfiguration)"]
    SRAC["SolrRepositoryAutoConfiguration<br/>@AutoConfigureAfter(SolrAutoConfiguration)"]
  end

  subgraph Beans["Beans Registered"]
    Client["SolrClient<br/>(HttpJdkSolrClient<br/> or CloudSolrClient)"]
    Template["SolrTemplate<br/>implements SolrOperations"]
    Health["SolrHealthIndicator<br/>(if Actuator on classpath)"]
    Registrar["SolrRepositoriesRegistrar<br/>(scans @EnableSolrRepositories)"]
    Repos["SolrRepository<T><br/>proxies"]
  end

  SFL --> SAC
  SFL --> SHAC
  SFL --> SRAC

  SP -.binds.-> SAC
  SAC --> Client
  SAC --> Template
  Client --> Template

  SHAC --> Health
  Template -.used by.-> Health

  SRAC --> Registrar
  Registrar --> Repos
  Template -.injected into.-> Repos
```

---

## 2. Request Flow: Repository Call to Solr

What actually happens when application code calls a derived query method on a
`SolrRepository`. Spring Data's PartTree mechanism is the bridge between method
names and Solr query strings.

```mermaid
sequenceDiagram
  autonumber
  participant App as Application Code
  participant Proxy as Repository Proxy<br/>(SolrRepositoryFactory)
  participant LS as SolrQueryLookupStrategy
  participant PT as PartTreeSolrQuery
  participant QC as SolrQueryCreator
  participant SQ as SimpleQuery
  participant Tmpl as SolrTemplate
  participant Conv as SolrMappingConverter
  participant Client as SolrClient
  participant Solr as Apache Solr

  App->>Proxy: findByTitleContaining("dune")
  Proxy->>LS: resolveQuery(method)
  LS->>PT: dispatch on method signature

  alt @Query annotation present
    PT->>SQ: StringBasedSolrQuery<br/>(positional ?0 substitution)
  else Derived from method name
    PT->>QC: parse PartTree
    QC->>SQ: build Criteria + SimpleQuery
  end

  PT->>Tmpl: queryForPage / queryForObject / count
  Tmpl->>SQ: toSolrQuery()
  Tmpl->>Client: query(collection, SolrQuery)
  Client->>Solr: HTTP request<br/>(/select, /update, ...)
  Solr-->>Client: QueryResponse
  Client-->>Tmpl: SolrDocumentList
  Tmpl->>Conv: read(SolrDocument -> T)
  Conv-->>Tmpl: domain objects
  Tmpl-->>PT: Page / List / T / long
  PT-->>App: typed result
```

---

## 3. Spring Boot Middleware Integration

A higher-level view: which Spring Boot subsystems the library plugs into, and
which Spring Data contracts it implements. This is the "what touches what"
map.

```mermaid
flowchart LR
  subgraph SB["Spring Boot"]
    direction TB
    Env["Environment<br/>+ @ConfigurationProperties"]
    Ctx["ApplicationContext"]
    Act["Spring Boot Actuator<br/>(HealthEndpoint)"]
    DC["Docker Compose Support<br/>(sample module)"]
  end

  subgraph SD["Spring Data Commons"]
    direction TB
    REPO["Repository&lt;T, ID&gt;"]
    CRUD["CrudRepository"]
    PSR["PagingAndSortingRepository"]
    RFB["RepositoryFactoryBeanSupport"]
    QLS["QueryLookupStrategy"]
    PTree["PartTree<br/>method parsing"]
  end

  subgraph Lib["spring-data-solr"]
    direction TB
    Props["SolrProperties"]
    AutoCfg["3 @AutoConfiguration<br/>classes"]
    Ops["SolrOperations<br/>+ SolrTemplate"]
    SolrRepo["SolrRepository<T>"]
    SimpleRepo["SimpleSolrRepository"]
    Factory["SolrRepositoryFactory<br/>+ FactoryBean"]
    Lookup["SolrQueryLookupStrategy"]
    Creator["SolrQueryCreator"]
    Mapper["SolrMappingConverter<br/>+ Reader/Writer"]
    HI["SolrHealthIndicator"]
  end

  subgraph SolrJ["Apache SolrJ 10"]
    Client["SolrClient<br/>(HttpJdk / Cloud)"]
  end

  Solr[("Apache Solr<br/>9 or 10")]

  Env --> Props
  Ctx --> AutoCfg
  AutoCfg --> Ops
  AutoCfg --> Factory
  AutoCfg --> HI

  Act --> HI
  HI --> Ops

  SolrRepo --> CRUD
  SolrRepo --> PSR
  CRUD --> REPO
  PSR --> REPO

  Factory --> RFB
  Factory --> Lookup
  Lookup --> QLS
  Lookup --> PTree
  PTree --> Creator
  Creator --> Ops
  SimpleRepo --> Ops
  Factory -.creates.-> SimpleRepo

  Ops --> Mapper
  Ops --> Client
  Client --> Solr

  DC -.starts.-> Solr
```

---

## 4. Configuration Surface

Where the user's `application.yml` lands and which beans it shapes.

```mermaid
flowchart TD
  YML["application.yml<br/>spring.solr.*"]
  SP["SolrProperties<br/>@ConfigurationProperties"]

  subgraph Decision["Client Selection (SolrAutoConfiguration)"]
    Q{"Both standalone<br/>and cloud set?"}
    Err["IllegalStateException<br/>(startup failure)"]
    QC{"spring.solr.cloud<br/>.zk-host set?"}
    Cloud["CloudSolrClient<br/>(ZooKeeper-aware)"]
    Std["HttpJdkSolrClient<br/>(standalone, JDK HttpClient)"]
  end

  Tmpl["SolrTemplate"]
  CM["CommitMode<br/>NONE | IMMEDIATE"]

  YML --> SP
  SP --> Q
  Q -- yes --> Err
  Q -- no --> QC
  QC -- yes --> Cloud
  QC -- no --> Std
  Cloud --> Tmpl
  Std --> Tmpl
  SP -- commit-mode --> CM
  CM --> Tmpl
```

---

*"Make it so."* — render these wherever the docs are served; they should stay
in lock-step with the auto-configuration classes and the `SolrTemplate` /
`SolrRepository` contracts. Update when a new `@AutoConfiguration` class is
added or the request-flow pipeline changes shape.
