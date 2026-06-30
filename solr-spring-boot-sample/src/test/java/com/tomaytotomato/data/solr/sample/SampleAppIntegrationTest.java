package com.tomaytotomato.data.solr.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.tomaytotomato.data.solr.SolrTemplate;
import com.tomaytotomato.data.solr.query.Criteria;
import com.tomaytotomato.data.solr.query.FacetOptions;
import com.tomaytotomato.data.solr.query.HighlightOptions;
import com.tomaytotomato.data.solr.query.SimpleQuery;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.SolrContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Sample App Integration Tests")
class SampleAppIntegrationTest {

  private static final String COLLECTION = "books";

  @Container
  static final SolrContainer solr = new SolrContainer(DockerImageName.parse("solr:10"))
      .withCollection(COLLECTION);

  @DynamicPropertySource
  static void solrProperties(DynamicPropertyRegistry registry) {
    var solrBaseUrl = "http://" + solr.getHost() + ":" + solr.getSolrPort() + "/solr";
    defineLocationField(solrBaseUrl);
    registry.add("spring.data.solr.standalone.host", () -> solrBaseUrl);
    registry.add("spring.data.solr.standalone.default-collection", () -> COLLECTION);
    registry.add("spring.data.solr.commit-mode", () -> "IMMEDIATE");
  }

  // Schemaless mode auto-types "lat,lon" as multiValued strings; SolrJ then returns
  // an ArrayList which can't bind to the entity's String field. Pre-defining the field
  // as LatLonPointSpatialField avoids this and enables geospatial queries.
  private static void defineLocationField(String solrBaseUrl) {
    try {
      var body = """
          {"add-field":{"name":"location","type":"location","stored":true,"indexed":true,"docValues":true}}""";
      var request = HttpRequest.newBuilder()
          .uri(URI.create(solrBaseUrl + "/" + COLLECTION + "/schema"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();
      var response = HttpClient.newHttpClient()
          .send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new RuntimeException("Failed to define location field: " + response.body());
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Could not configure Solr schema for location field", e);
    }
  }

  @Autowired
  BookRepository bookRepository;

  @Autowired
  SolrTemplate solrTemplate;

  @Nested
  @DisplayName("Data Loading")
  class DataLoading {

    @Test
    @DisplayName("loads all curated books from JSON on startup")
    void loadsAllCuratedBooks() {
      assertThat(bookRepository.count()).isEqualTo(250);
    }

    @Test
    @DisplayName("books have all expected fields populated")
    void booksHaveExpectedFields() {
      var book = bookRepository.findById("1");
      assertThat(book).isPresent();
      assertThat(book.get().getTitle()).isNotBlank();
      assertThat(book.get().getAuthor()).isNotBlank();
      assertThat(book.get().getDescription()).isNotBlank();
      assertThat(book.get().getCategories()).isNotEmpty();
      assertThat(book.get().getYear()).isGreaterThan(0);
      assertThat(book.get().getPages()).isGreaterThan(0);
      assertThat(book.get().getPrice()).isGreaterThan(0);
    }
  }

  @Nested
  @DisplayName("Derived Queries")
  class DerivedQueries {

    @Test
    @DisplayName("finds books by title containing a keyword")
    void findByTitleContaining() {
      var results = bookRepository.findByTitleContaining("the");
      assertThat(results).isNotEmpty();
      assertThat(results).allSatisfy(book ->
          assertThat(book.getTitle().toLowerCase()).contains("the"));
    }

    @Test
    @DisplayName("finds books by title starting with a prefix")
    void findByTitleStartingWith() {
      var results = bookRepository.findByTitleStartingWith("The");
      assertThat(results).isNotEmpty();
      // text_general tokenises, so the prefix matches any token starting with "the"
      assertThat(results).allSatisfy(book ->
          assertThat(book.getTitle().toLowerCase()).contains("the"));
    }

    @Test
    @DisplayName("finds books in stock")
    void findByInStock() {
      var inStock = bookRepository.findByInStock(true);
      var outOfStock = bookRepository.findByInStock(false);
      assertThat(inStock).isNotEmpty();
      assertThat(outOfStock).isNotEmpty();
      assertThat(inStock).allSatisfy(book -> assertThat(book.isInStock()).isTrue());
      assertThat(outOfStock).allSatisfy(book -> assertThat(book.isInStock()).isFalse());
    }

    @Test
    @DisplayName("finds books by price range")
    void findByPriceBetween() {
      var results = bookRepository.findByPriceBetween(10.0, 20.0);
      assertThat(results).isNotEmpty();
      assertThat(results).allSatisfy(book -> {
        assertThat(book.getPrice()).isGreaterThanOrEqualTo(10.0);
        assertThat(book.getPrice()).isLessThanOrEqualTo(20.0);
      });
    }

    @Test
    @DisplayName("finds books by minimum rating")
    void findByRatingGreaterThanEqual() {
      var results = bookRepository.findByRatingGreaterThanEqual(4.5);
      assertThat(results).isNotEmpty();
      assertThat(results).allSatisfy(book ->
          assertThat(book.getRating()).isGreaterThanOrEqualTo(4.5));
    }

    @Test
    @DisplayName("finds books by year range")
    void findByYearBetween() {
      var results = bookRepository.findByYearBetween(2000, 2010);
      assertThat(results).isNotEmpty();
      assertThat(results).allSatisfy(book -> {
        assertThat(book.getYear()).isGreaterThanOrEqualTo(2000);
        assertThat(book.getYear()).isLessThanOrEqualTo(2010);
      });
    }

    @Test
    @DisplayName("counts books by author")
    void countByAuthor() {
      var firstBook = bookRepository.findById("1").orElseThrow();
      var count = bookRepository.countByAuthor(firstBook.getAuthor());
      assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("checks existence by title")
    void existsByTitle() {
      var firstBook = bookRepository.findById("1").orElseThrow();
      assertThat(bookRepository.existsByTitle(firstBook.getTitle())).isTrue();
      assertThat(bookRepository.existsByTitle("zzzzqqqxxx99999")).isFalse();
    }
  }

  @Nested
  @DisplayName("Custom @Query Methods")
  class CustomQueries {

    @Test
    @DisplayName("counts books by category")
    void countByCategoryCustom() {
      var count = bookRepository.countByCategoryCustom("Fiction");
      assertThat(count).isGreaterThan(0);
    }

    @Test
    @DisplayName("finds books by category and price range")
    void findByCategoryAndPriceRange() {
      var results = bookRepository.findByCategoryAndPriceRange("Fiction", 5.0, 50.0);
      assertThat(results).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Highlighting")
  class Highlighting {

    @Test
    @DisplayName("returns highlight snippets for matching description text")
    void highlightDescriptions() {
      var query = new SimpleQuery(Criteria.where("description_t").contains("novel"));
      query.setPageable(PageRequest.of(0, 5));
      query.setHighlightOptions(new HighlightOptions()
          .addField("description_t")
          .preTag("<em>")
          .postTag("</em>")
          .snippets(1)
          .fragsize(200));
      var page = solrTemplate.queryForHighlightPage(COLLECTION, query, Book.class);

      assertThat(page.getContent()).isNotEmpty();
      assertThat(page.getHighlighted()).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Faceting")
  class Faceting {

    @Test
    @DisplayName("returns facet counts by category")
    void facetByCategory() {
      var query = new SimpleQuery(Criteria.where("*").expression("*"));
      query.setPageable(PageRequest.of(0, 1));
      query.setFacetOptions(new FacetOptions()
          .addFacetOnField("categories_ss")
          .minCount(1)
          .limit(50));
      var page = solrTemplate.queryForFacetPage(COLLECTION, query, Book.class);

      assertThat(page.getFacetFields()).containsKey("categories_ss");
      var facets = page.getFacetFields().get("categories_ss");
      assertThat(facets).isNotEmpty();
      assertThat(facets.stream().map(com.tomaytotomato.data.solr.query.FacetFieldEntry::value))
          .contains("Fiction");
    }
  }

  @Nested
  @DisplayName("Cursor Paging")
  class CursorPaging {

    @Test
    @DisplayName("pages through all results using cursor marks")
    void cursorPaging() {
      var query = new SimpleQuery(Criteria.where("*").expression("*"));
      query.setCursorMark("*");
      query.setPageable(PageRequest.of(0, 50, Sort.by("id").ascending()));

      var firstPage = solrTemplate.queryWithCursor(COLLECTION, query, Book.class);
      assertThat(firstPage.content()).hasSize(50);
      assertThat(firstPage.cursorMark()).isNotEqualTo("*");

      query.setCursorMark(firstPage.cursorMark());
      var secondPage = solrTemplate.queryWithCursor(COLLECTION, query, Book.class);
      assertThat(secondPage.content()).hasSize(50);
      assertThat(secondPage.cursorMark()).isNotEqualTo(firstPage.cursorMark());
    }
  }

  @Nested
  @DisplayName("CRUD Operations")
  class CrudOperations {

    @Test
    @DisplayName("saves and retrieves a new book")
    void saveAndRetrieve() {
      var book = new Book();
      book.setId("test-999");
      book.setTitle("Integration Test Book");
      book.setAuthor("Test Author");
      book.setDescription("A book created during integration testing");
      book.setCategories(List.of("Testing"));
      book.setRating(5.0);
      book.setYear(2026);
      book.setPages(100);
      book.setPrice(19.99);
      book.setInStock(true);

      bookRepository.save(book);

      var found = bookRepository.findById("test-999");
      assertThat(found).isPresent();
      assertThat(found.get().getTitle()).isEqualTo("Integration Test Book");
      assertThat(found.get().getCategories()).containsExactly("Testing");

      bookRepository.deleteById("test-999");
      assertThat(bookRepository.findById("test-999")).isEmpty();
    }
  }

  @Nested
  @DisplayName("Partial Updates")
  class PartialUpdates {

    @Test
    @DisplayName("updates a single field without replacing the entire document")
    void partialUpdatePrice() {
      var original = bookRepository.findById("1").orElseThrow();
      var originalTitle = original.getTitle();

      var update = new com.tomaytotomato.data.solr.PartialUpdate("1")
          .set("price_d", 99.99);
      solrTemplate.savePartialUpdate(COLLECTION, update);

      var updated = bookRepository.findById("1").orElseThrow();
      assertThat(updated.getPrice()).isEqualTo(99.99);
      assertThat(updated.getTitle()).isEqualTo(originalTitle);
    }
  }
}
