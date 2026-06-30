package com.tomaytotomato.data.solr;

import com.tomaytotomato.data.solr.health.SolrHealthIndicator;
import com.tomaytotomato.data.solr.mapping.SolrConverterRegistration;
import com.tomaytotomato.data.solr.mapping.SolrCustomConversions;
import com.tomaytotomato.data.solr.query.Criteria;
import com.tomaytotomato.data.solr.query.FacetOptions;
import com.tomaytotomato.data.solr.query.GeoDistance;
import com.tomaytotomato.data.solr.query.GeoPoint;
import com.tomaytotomato.data.solr.query.HighlightOptions;
import com.tomaytotomato.data.solr.query.SimpleQuery;
import com.tomaytotomato.data.solr.query.StreamingExpression;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.beans.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.testcontainers.containers.SolrContainer;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractSolrIntegrationTest {

  static final String COLLECTION = "books";

  abstract SolrContainer getSolrContainer();

  private ApplicationContextRunner contextRunner;

  @BeforeEach
  void setUp() {
    var solr = getSolrContainer();
    contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SolrAutoConfiguration.class))
        .withPropertyValues(
            "spring.data.solr.standalone.host=http://" + solr.getHost() + ":" + solr.getSolrPort() + "/solr",
            "spring.data.solr.standalone.default-collection=" + COLLECTION,
            "spring.data.solr.commit-mode=IMMEDIATE"
        );

    contextRunner.run(ctx -> {
      var template = ctx.getBean(SolrTemplate.class);
      template.deleteByQuery(COLLECTION, "*:*");
      template.commit(COLLECTION);
    });
  }

  static TestBook book(String id, String title, String author, int year) {
    var book = new TestBook();
    book.id = id;
    book.title = title;
    book.author = author;
    book.year = year;
    return book;
  }

  public static class TestBook {
    @Field
    String id;

    @Field("title_s")
    String title;

    @Field("author_s")
    String author;

    @Field("year_i")
    int year;
  }

  @Nested
  class SaveAndRetrieve {

    @Test
    void savesDocumentAndRetrievesItByIdWithAllFields() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        var picard = book("1", "Make It So", "Jean-Luc Picard", 2350);

        template.save(COLLECTION, picard);

        var found = template.findById(COLLECTION, "1", TestBook.class);
        assertThat(found).isPresent();
        assertThat(found.get().title).isEqualTo("Make It So");
        assertThat(found.get().author).isEqualTo("Jean-Luc Picard");
        assertThat(found.get().year).isEqualTo(2350);
      });
    }

    @Test
    void returnsEmptyOptionalForNonExistentId() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);

        var found = template.findById(COLLECTION, "does-not-exist", TestBook.class);

        assertThat(found).isEmpty();
      });
    }

    @Test
    void savesMultipleDocumentsAndAllAreQueryable() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        var books = List.of(
            book("10", "The Hollow Men", "T.S. Eliot", 1925),
            book("11", "Heart of Darkness", "Joseph Conrad", 1899),
            book("12", "The Great Gatsby", "F. Scott Fitzgerald", 1925)
        );

        template.saveAll(COLLECTION, books);

        var query = new SimpleQuery(Criteria.where("id").in("10", "11", "12"));
        var results = template.queryForPage(COLLECTION, query, TestBook.class);
        assertThat(results.getTotalElements()).isEqualTo(3);
        assertThat(results.getContent()).extracting(b -> b.id)
            .containsExactlyInAnyOrder("10", "11", "12");
      });
    }
  }

  @Nested
  class QueryOperations {

    @Test
    void queriesDocumentsByFieldValueUsingCriteria() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("20", "Dune", "Frank Herbert", 1965),
            book("21", "Foundation", "Isaac Asimov", 1951),
            book("22", "Dune Messiah", "Frank Herbert", 1969)
        ));

        var query = new SimpleQuery(Criteria.where("author_s").is("Frank Herbert"));
        var results = template.queryForPage(COLLECTION, query, TestBook.class);

        assertThat(results.getTotalElements()).isEqualTo(2);
        assertThat(results.getContent()).extracting(b -> b.title)
            .containsExactlyInAnyOrder("Dune", "Dune Messiah");
      });
    }

    @Test
    void countReturnsNumberOfMatchingDocuments() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("30", "1984", "George Orwell", 1949),
            book("31", "Animal Farm", "George Orwell", 1945),
            book("32", "Brave New World", "Aldous Huxley", 1932)
        ));

        var count = template.count(COLLECTION, new SimpleQuery(Criteria.where("author_s").is("George Orwell")));

        assertThat(count).isEqualTo(2);
      });
    }

    @Test
    void queryForPageReturnsPaginatedResultsWithCorrectTotalCount() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("40", "Book A", "Author X", 2000),
            book("41", "Book B", "Author X", 2001),
            book("42", "Book C", "Author X", 2002),
            book("43", "Book D", "Author X", 2003),
            book("44", "Book E", "Author X", 2004)
        ));

        var pageable = PageRequest.of(0, 2);
        var query = new SimpleQuery(Criteria.where("author_s").is("Author X"), pageable);
        var page = template.queryForPage(COLLECTION, query, TestBook.class);

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalPages()).isEqualTo(3);
      });
    }

    @Test
    void criteriaRangeQueryFiltersDocumentsByYear() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("50", "Old Book", "Author Y", 1800),
            book("51", "Modern Book", "Author Y", 2000),
            book("52", "Future Book", "Author Y", 2100)
        ));

        var query = new SimpleQuery(Criteria.where("year_i").between(1900, 2099));
        var results = template.queryForPage(COLLECTION, query, TestBook.class);

        assertThat(results.getContent()).extracting(b -> b.title)
            .containsExactlyInAnyOrder("Modern Book");
      });
    }
  }

  @Nested
  class DeleteOperations {

    @Test
    void deleteByIdRemovesOnlyTheTargetDocument() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("60", "Stay", "Author Z", 2020),
            book("61", "Go", "Author Z", 2021)
        ));

        template.deleteById(COLLECTION, "61");
        template.commit(COLLECTION);

        assertThat(template.findById(COLLECTION, "61", TestBook.class)).isEmpty();
        assertThat(template.findById(COLLECTION, "60", TestBook.class)).isPresent();
      });
    }

    @Test
    void deleteByQueryRemovesAllMatchingDocuments() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("70", "Kept", "Author Keep", 2020),
            book("71", "Purged One", "Author Delete", 2021),
            book("72", "Purged Two", "Author Delete", 2022)
        ));

        template.deleteByQuery(COLLECTION, "author_s:\"Author Delete\"");
        template.commit(COLLECTION);

        assertThat(template.count(COLLECTION, new SimpleQuery(Criteria.where("author_s").is("Author Delete")))).isZero();
        assertThat(template.findById(COLLECTION, "70", TestBook.class)).isPresent();
      });
    }
  }

  @Nested
  class HealthIndicator {

    @Test
    void reportsUpWithCollectionDetailWhenSolrIsReachable() {
      contextRunner.run(ctx -> {
        var solrClient = ctx.getBean(SolrClient.class);
        var indicator = new SolrHealthIndicator(solrClient, COLLECTION);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("collection", COLLECTION);
        assertThat(health.getDetails()).containsKey("elapsed_time_ms");
      });
    }

    @Test
    void reportsUpWithSolrVersionWhenNoCollectionConfigured() {
      contextRunner.run(ctx -> {
        var solrClient = ctx.getBean(SolrClient.class);
        var indicator = new SolrHealthIndicator(solrClient, null);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("solr-version");
      });
    }
  }

  @Nested
  class CursorPaging {

    @Test
    void firstPageHasRequestedSizeAndSignalsMoreResults() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("c1", "Alpha", "Author C", 2001),
            book("c2", "Beta", "Author C", 2002),
            book("c3", "Gamma", "Author C", 2003),
            book("c4", "Delta", "Author C", 2004),
            book("c5", "Epsilon", "Author C", 2005),
            book("c6", "Zeta", "Author C", 2006),
            book("c7", "Eta", "Author C", 2007),
            book("c8", "Theta", "Author C", 2008),
            book("c9", "Iota", "Author C", 2009),
            book("c10", "Kappa", "Author C", 2010),
            book("c11", "Lambda", "Author C", 2011)
        ));

        var query = new SimpleQuery(Criteria.where("*").expression("*"))
            .setSort(Sort.by(Sort.Direction.ASC, "id"))
            .setPageable(PageRequest.of(0, 3))
            .setCursorMark("*");

        var firstPage = template.queryWithCursor(COLLECTION, query, TestBook.class);

        assertThat(firstPage.content()).hasSize(3);
        assertThat(firstPage.hasMore()).isTrue();
      });
    }

    @Test
    void subsequentPageHasDifferentResultsThanFirstPage() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("p1", "Alpha", "Author P", 2001),
            book("p2", "Beta", "Author P", 2002),
            book("p3", "Gamma", "Author P", 2003),
            book("p4", "Delta", "Author P", 2004),
            book("p5", "Epsilon", "Author P", 2005),
            book("p6", "Zeta", "Author P", 2006)
        ));

        var firstQuery = new SimpleQuery(Criteria.where("author_s").is("Author P"))
            .setSort(Sort.by(Sort.Direction.ASC, "id"))
            .setPageable(PageRequest.of(0, 3))
            .setCursorMark("*");
        var firstPage = template.queryWithCursor(COLLECTION, firstQuery, TestBook.class);

        var secondQuery = new SimpleQuery(Criteria.where("author_s").is("Author P"))
            .setSort(Sort.by(Sort.Direction.ASC, "id"))
            .setPageable(PageRequest.of(0, 3))
            .setCursorMark(firstPage.cursorMark());
        var secondPage = template.queryWithCursor(COLLECTION, secondQuery, TestBook.class);

        var firstIds = firstPage.content().stream().map(b -> b.id).toList();
        var secondIds = secondPage.content().stream().map(b -> b.id).toList();
        assertThat(secondIds).isNotEmpty();
        assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);
      });
    }

    @Test
    void traversingAllPagesYieldsTotalSeedCount() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("t1", "Book 1", "Author T", 2001),
            book("t2", "Book 2", "Author T", 2002),
            book("t3", "Book 3", "Author T", 2003),
            book("t4", "Book 4", "Author T", 2004),
            book("t5", "Book 5", "Author T", 2005),
            book("t6", "Book 6", "Author T", 2006),
            book("t7", "Book 7", "Author T", 2007),
            book("t8", "Book 8", "Author T", 2008),
            book("t9", "Book 9", "Author T", 2009),
            book("t10", "Book 10", "Author T", 2010),
            book("t11", "Book 11", "Author T", 2011)
        ));

        var allResults = new ArrayList<TestBook>();
        var cursor = "*";
        CursorResult<TestBook> page;

        do {
          var query = new SimpleQuery(Criteria.where("author_s").is("Author T"))
              .setSort(Sort.by(Sort.Direction.ASC, "id"))
              .setPageable(PageRequest.of(0, 4))
              .setCursorMark(cursor);
          page = template.queryWithCursor(COLLECTION, query, TestBook.class);
          allResults.addAll(page.content());
          cursor = page.cursorMark();
        } while (page.hasMore());

        assertThat(allResults).hasSize(11);
      });
    }
  }

  @Nested
  class Faceting {

    @Test
    void fieldFacetCountsReflectSeedDistribution() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("f1", "Book 1", "Author A", 2001),
            book("f2", "Book 2", "Author A", 2002),
            book("f3", "Book 3", "Author A", 2003),
            book("f4", "Book 4", "Author B", 2004),
            book("f5", "Book 5", "Author B", 2005),
            book("f6", "Book 6", "Author C", 2006)
        ));

        var query = new SimpleQuery(Criteria.where("*").expression("*"))
            .setFacetOptions(new FacetOptions().addFacetOnField("author_s"));

        var facetPage = template.queryForFacetPage(COLLECTION, query, TestBook.class);

        assertThat(facetPage.getFacetFields()).containsKey("author_s");
        var authorFacets = facetPage.getFacetField("author_s");
        assertThat(authorFacets).extracting(e -> e.value())
            .containsExactlyInAnyOrder("Author A", "Author B", "Author C");
        assertThat(authorFacets).filteredOn(e -> e.value().equals("Author A"))
            .singleElement()
            .extracting(e -> e.count())
            .isEqualTo(3L);
        assertThat(authorFacets).filteredOn(e -> e.value().equals("Author B"))
            .singleElement()
            .extracting(e -> e.count())
            .isEqualTo(2L);
        assertThat(authorFacets).filteredOn(e -> e.value().equals("Author C"))
            .singleElement()
            .extracting(e -> e.count())
            .isEqualTo(1L);
      });
    }

    @Test
    void queryFacetCountsMatchFilteredDocuments() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("q1", "Old Book", "Author Q", 1990),
            book("q2", "Modern Book", "Author Q", 2005),
            book("q3", "New Book", "Author Q", 2020)
        ));

        var query = new SimpleQuery(Criteria.where("*").expression("*"))
            .setFacetOptions(new FacetOptions()
                .addFacetQuery("year_i:[2000 TO *]")
                .addFacetQuery("year_i:[* TO 1999]"));

        var facetPage = template.queryForFacetPage(COLLECTION, query, TestBook.class);

        assertThat(facetPage.getFacetQueries()).isNotEmpty();
        assertThat(facetPage.getFacetQueries())
            .filteredOn(e -> e.query().equals("year_i:[2000 TO *]"))
            .singleElement()
            .extracting(e -> e.count())
            .isEqualTo(2L);
        assertThat(facetPage.getFacetQueries())
            .filteredOn(e -> e.query().equals("year_i:[* TO 1999]"))
            .singleElement()
            .extracting(e -> e.count())
            .isEqualTo(1L);
      });
    }
  }

  @Nested
  class Highlighting {

    /**
     * title_s is a Solr StrField (non-tokenised exact-match). The unified highlighter
     * produces snippets when the query term exactly matches the stored field value.
     * We therefore seed books whose title is exactly the query term.
     */
    @Test
    void highlightedSnippetsContainTaggedTermForMatchingDocuments() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("h1", "Spring", "Author H", 2020),
            book("h2", "Spring", "Author H", 2021),
            book("h3", "Winter", "Author H", 2022)
        ));

        var query = new SimpleQuery(Criteria.where("title_s").is("Spring"))
            .setHighlightOptions(new HighlightOptions()
                .addField("title_s")
                .preTag("<em>")
                .postTag("</em>")
                .fragsize(0));

        var highlightPage = template.queryForHighlightPage(COLLECTION, query, TestBook.class);

        assertThat(highlightPage.getContent()).hasSize(2);
        var allSnippets = highlightPage.getHighlighted().stream()
            .flatMap(entry -> entry.highlights().values().stream())
            .flatMap(List::stream)
            .toList();
        assertThat(allSnippets).isNotEmpty();
        assertThat(allSnippets).allSatisfy(snippet ->
            assertThat(snippet).contains("<em>Spring</em>"));
      });
    }

    @Test
    void highlightPageContainsEntryForEachReturnedDocument() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("h4", "Spring", "Author H", 2020),
            book("h5", "Winter", "Author H", 2021)
        ));

        var query = new SimpleQuery(Criteria.where("title_s").is("Spring"))
            .setHighlightOptions(new HighlightOptions()
                .addField("title_s")
                .preTag("<em>")
                .postTag("</em>")
                .fragsize(0));

        var highlightPage = template.queryForHighlightPage(COLLECTION, query, TestBook.class);

        assertThat(highlightPage.getContent()).hasSize(1);
        assertThat(highlightPage.getHighlighted()).hasSize(1);
        assertThat(highlightPage.getHighlighted().getFirst().highlights())
            .containsKey("title_s");
      });
    }
  }

  // -------------------------------------------------------------------------
  // Geospatial fixtures
  // -------------------------------------------------------------------------

  /**
   * A minimal Solr entity carrying a LatLonPointSpatialField value.
   * The {@code location} field must be defined in the schema before indexing —
   * see {@link GeoSpatial#defineLocationField()}.
   */
  public static class TestPlace {
    @Field
    String id;

    @Field("name_s")
    String name;

    /**
     * Stored as {@code "lat,lon"} on write. On read, Solr may return an
     * {@code ArrayList<Double>} when docValues are active; {@link
     * com.tomaytotomato.data.solr.mapping.SolrDocumentReader} coerces it back to a
     * comma-joined String.
     */
    @Field("location")
    String location;
  }

  static TestPlace place(String id, String name, double lat, double lon) {
    var p = new TestPlace();
    p.id = id;
    p.name = name;
    p.location = lat + "," + lon;
    return p;
  }

  // Known city coordinates
  static final GeoPoint LONDON    = new GeoPoint(51.5074, -0.1278);
  static final GeoPoint PARIS     = new GeoPoint(48.8566,  2.3522);
  static final GeoPoint EDINBURGH = new GeoPoint(55.9533, -3.1883);
  static final GeoPoint TOKYO     = new GeoPoint(35.6762, 139.6503);

  /**
   * Registers the {@code location} field (type {@code location} =
   * {@code LatLonPointSpatialField}) via Solr's Schema API.
   *
   * <p>The {@code _default} configset ships with the {@code location} field <em>type</em>
   * pre-registered; we only need to add the concrete field definition. Solr returns HTTP 400
   * if the field already exists — that is treated as a no-op so the call is idempotent.
   */
  void defineLocationField() throws Exception {
    var solr = getSolrContainer();
    var url = "http://" + solr.getHost() + ":" + solr.getSolrPort()
        + "/solr/" + COLLECTION + "/schema";
    var body = """
        {"add-field":{"name":"location","type":"location","indexed":true,"stored":true}}
        """;
    var request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
    var response = HttpClient.newHttpClient()
        .send(request, HttpResponse.BodyHandlers.ofString());
    // 200 = added, 400 = already exists (idempotent) — both are acceptable
    assertThat(response.statusCode())
        .as("Schema API response for add-field location: %s", response.body())
        .isIn(200, 400);
  }

  @Nested
  class GeoSpatial {

    @BeforeEach
    void defineLocationField() throws Exception {
      AbstractSolrIntegrationTest.this.defineLocationField();
    }

    @Test
    void nearQueryReturnsDocumentsWithinRadiusInKilometres() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            place("g1", "London",    51.5074,  -0.1278),
            place("g2", "Edinburgh", 55.9533,  -3.1883),
            place("g3", "Tokyo",     35.6762, 139.6503)
        ));

        // Search within 100 km of London — should find London, not Edinburgh (~534 km away)
        var query = new SimpleQuery(
            Criteria.where("location").near(LONDON, GeoDistance.kilometers(100)));
        var results = template.queryForPage(COLLECTION, query, TestPlace.class);

        assertThat(results.getContent()).extracting(p -> p.name)
            .containsExactly("London")
            .doesNotContain("Edinburgh", "Tokyo");
      });
    }

    @Test
    void nearQueryReturnsDocumentsWithinRadiusInMiles() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            place("g4", "London",    51.5074,  -0.1278),
            place("g5", "Edinburgh", 55.9533,  -3.1883),
            place("g6", "Tokyo",     35.6762, 139.6503)
        ));

        // 62 miles ≈ 99.8 km — inside London, outside Edinburgh
        var query = new SimpleQuery(
            Criteria.where("location").near(LONDON, GeoDistance.miles(62)));
        var results = template.queryForPage(COLLECTION, query, TestPlace.class);

        assertThat(results.getContent()).extracting(p -> p.name)
            .containsExactly("London")
            .doesNotContain("Edinburgh", "Tokyo");
      });
    }

    @Test
    void withinQueryReturnsBoundingBoxResults() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            place("g7", "Paris",     48.8566,  2.3522),
            place("g8", "London",    51.5074, -0.1278),
            place("g9", "Tokyo",     35.6762, 139.6503)
        ));

        // 200 km bounding box around Paris — should find Paris; London is ~340 km away
        var query = new SimpleQuery(
            Criteria.where("location").within(PARIS, GeoDistance.kilometers(200)));
        var results = template.queryForPage(COLLECTION, query, TestPlace.class);

        assertThat(results.getContent()).extracting(p -> p.name)
            .containsExactly("Paris")
            .doesNotContain("London", "Tokyo");
      });
    }

    @Test
    void documentOutsideRadiusIsExcluded() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            place("g10", "London",    51.5074,  -0.1278),
            place("g11", "Edinburgh", 55.9533,  -3.1883),
            place("g12", "Paris",     48.8566,   2.3522),
            place("g13", "Tokyo",     35.6762, 139.6503)
        ));

        // Tight 10 km radius around London city centre — only London itself qualifies
        var query = new SimpleQuery(
            Criteria.where("location").near(LONDON, GeoDistance.kilometers(10)));
        var results = template.queryForPage(COLLECTION, query, TestPlace.class);

        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().getFirst().name).isEqualTo("London");
      });
    }
  }

  @Nested
  class StreamingExpressions {

    @Test
    void searchExpressionReturnsIndexedDocumentsExcludingEofTuple() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("s1", "Dune", "Frank Herbert", 1965),
            book("s2", "Foundation", "Isaac Asimov", 1951),
            book("s3", "Neuromancer", "William Gibson", 1984)
        ));

        var expression = StreamingExpression.search(COLLECTION)
            .query("*:*")
            .fields("id", "title_s", "author_s")
            .sort("id asc")
            .rows(10)
            .build();

        var results = template.stream(COLLECTION, expression);

        assertThat(results).hasSize(3);
        assertThat(results).extracting(m -> m.get("id"))
            .containsExactly("s1", "s2", "s3");
      });
    }

    @Test
    void streamResultDoesNotContainEofTuple() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("e1", "The Left Hand of Darkness", "Ursula K. Le Guin", 1969),
            book("e2", "A Wizard of Earthsea", "Ursula K. Le Guin", 1968)
        ));

        var expression = StreamingExpression.search(COLLECTION)
            .query("author_s:\"Ursula K. Le Guin\"")
            .fields("id", "title_s")
            .sort("id asc")
            .rows(10)
            .build();

        var results = template.stream(COLLECTION, expression);

        assertThat(results).noneMatch(m -> m.containsKey("EOF"));
        assertThat(results).hasSize(2);
      });
    }

    @Test
    void streamResultContainsRequestedFields() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("r1", "Snow Crash", "Neal Stephenson", 1992)
        ));

        var expression = StreamingExpression.search(COLLECTION)
            .query("id:r1")
            .fields("id", "title_s", "author_s", "year_i")
            .sort("id asc")
            .rows(1)
            .build();

        var results = template.stream(COLLECTION, expression);

        assertThat(results).hasSize(1);
        Map<String, Object> doc = results.getFirst();
        assertThat(doc).containsKey("id");
        assertThat(doc).containsKey("title_s");
        assertThat(doc).containsKey("author_s");
        assertThat(doc.get("title_s")).isEqualTo("Snow Crash");
        assertThat(doc.get("author_s")).isEqualTo("Neal Stephenson");
      });
    }

    @Test
    void rawExpressionViaOfReturnsResults() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.saveAll(COLLECTION, List.of(
            book("w1", "The Dispossessed", "Ursula K. Le Guin", 1974),
            book("w2", "The Word for World is Forest", "Ursula K. Le Guin", 1972),
            book("w3", "Contact", "Carl Sagan", 1985)
        ));

        var rawExpr = "search(" + COLLECTION + ", q=\"*:*\", fl=\"id,title_s\", sort=\"id asc\", rows=10)";
        var expression = StreamingExpression.of(rawExpr);

        var results = template.stream(COLLECTION, expression);

        assertThat(results).hasSizeGreaterThanOrEqualTo(3);
        assertThat(results).noneMatch(m -> m.containsKey("EOF"));
      });
    }

    @Test
    void emptyCollectionReturnsEmptyList() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);

        var expression = StreamingExpression.search(COLLECTION)
            .query("*:*")
            .fields("id")
            .sort("id asc")
            .rows(10)
            .build();

        var results = template.stream(COLLECTION, expression);

        assertThat(results).isEmpty();
      });
    }
  }

  // -------------------------------------------------------------------------
  // Custom conversion pipeline fixtures
  // -------------------------------------------------------------------------

  /**
   * Minimal entity that stores a publication date as an ISO-8601 string in Solr
   * ({@code pub_date_s}) but exposes it as a {@link LocalDate} in Java.
   * A user-registered {@code String → LocalDate} converter should be applied
   * on read, and a {@code LocalDate → String} converter on write.
   */
  public static class DatedBook {

    @Field("id")
    public String id;

    @Field("pub_date_s")
    public LocalDate publishedDate;
  }

  static DatedBook datedBook(String id, LocalDate date) {
    var book = new DatedBook();
    book.id = id;
    book.publishedDate = date;
    return book;
  }

  @Nested
  class CustomConversions {

    @Test
    void userRegisteredConvertersAreAppliedDuringWriteAndRead() {
      var writeConverter = SolrConverterRegistration.of(
          LocalDate.class, String.class, LocalDate::toString);
      var readConverter = SolrConverterRegistration.of(
          String.class, LocalDate.class, LocalDate::parse);

      var customConversions = new SolrCustomConversions(List.of(writeConverter, readConverter));

      contextRunner
          .withBean(SolrCustomConversions.class, () -> customConversions)
          .run(ctx -> {
            var template = ctx.getBean(SolrTemplate.class);
            var date = LocalDate.of(2024, 6, 1);
            var book = datedBook("conv-1", date);

            template.save(COLLECTION, book);

            var found = template.findById(COLLECTION, "conv-1", DatedBook.class);
            assertThat(found).isPresent();
            assertThat(found.get().publishedDate).isEqualTo(date);
          });
    }

    @Test
    void writeConverterTransformsLocalDateToStringInSolrDocument() {
      var writeConverter = SolrConverterRegistration.of(
          LocalDate.class, String.class, LocalDate::toString);
      var readConverter = SolrConverterRegistration.of(
          String.class, LocalDate.class, LocalDate::parse);

      var customConversions = new SolrCustomConversions(List.of(writeConverter, readConverter));

      contextRunner
          .withBean(SolrCustomConversions.class, () -> customConversions)
          .run(ctx -> {
            var template = ctx.getBean(SolrTemplate.class);
            var date = LocalDate.of(1965, 8, 1);
            template.save(COLLECTION, datedBook("conv-2", date));

            var query = new SimpleQuery(Criteria.where("pub_date_s").is("1965-08-01"));
            var results = template.queryForPage(COLLECTION, query, DatedBook.class);

            assertThat(results.getTotalElements()).isEqualTo(1);
            assertThat(results.getContent().getFirst().publishedDate)
                .isEqualTo(LocalDate.of(1965, 8, 1));
          });
    }

    @Test
    void withoutCustomConverterLocalDateFieldIsNotMapped() {
      contextRunner.run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        var book = datedBook("conv-3", LocalDate.of(2024, 1, 1));

        // Without converters the write should not crash but the field won't round-trip
        // as a LocalDate — the assertion verifies the current (broken) baseline and
        // acts as a contrast to the converter-enabled test above.
        template.save(COLLECTION, book);

        var found = template.findById(COLLECTION, "conv-3", DatedBook.class);
        // Without a converter the date field won't be readable as LocalDate
        assertThat(found).isPresent();
        assertThat(found.get().publishedDate).isNull();
      });
    }
  }
}
