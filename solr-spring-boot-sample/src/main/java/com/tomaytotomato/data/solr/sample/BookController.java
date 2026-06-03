package com.tomaytotomato.data.solr.sample;

import com.tomaytotomato.data.solr.CursorResult;
import com.tomaytotomato.data.solr.FacetPage;
import com.tomaytotomato.data.solr.HighlightPage;
import com.tomaytotomato.data.solr.SolrTemplate;
import com.tomaytotomato.data.solr.query.Criteria;
import com.tomaytotomato.data.solr.query.FacetOptions;
import com.tomaytotomato.data.solr.query.GeoDistance;
import com.tomaytotomato.data.solr.query.GeoPoint;
import com.tomaytotomato.data.solr.query.HighlightOptions;
import com.tomaytotomato.data.solr.query.SimpleQuery;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

  private static final String COLLECTION = "books";

  private final BookRepository bookRepository;
  private final SolrTemplate solrTemplate;

  public BookController(BookRepository bookRepository, SolrTemplate solrTemplate) {
    this.bookRepository = bookRepository;
    this.solrTemplate = solrTemplate;
  }

  @GetMapping
  public Iterable<Book> findAll() {
    return bookRepository.findAll();
  }

  @GetMapping("/{id}")
  public ResponseEntity<Book> findById(@PathVariable String id) {
    return bookRepository.findById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/search")
  public List<Book> search(@RequestParam String q) {
    return bookRepository.findByTitleContaining(q);
  }

  @GetMapping("/starting-with")
  public List<Book> startingWith(@RequestParam String prefix) {
    return bookRepository.findByTitleStartingWith(prefix);
  }

  @GetMapping("/by-author")
  public List<Book> findByAuthor(@RequestParam String author) {
    return bookRepository.findByAuthor(author);
  }

  @GetMapping("/by-author-after")
  public Page<Book> findByAuthorAfterYear(@RequestParam String author,
      @RequestParam int year, Pageable pageable) {
    return bookRepository.findByAuthorAndYearGreaterThan(author, year, pageable);
  }

  @GetMapping("/in-stock")
  public List<Book> findInStock() {
    return bookRepository.findByInStock(true);
  }

  @GetMapping("/top-rated")
  public List<Book> findTopRated(@RequestParam(defaultValue = "4.0") double minRating) {
    return bookRepository.findByRatingGreaterThanEqual(minRating);
  }

  @GetMapping("/price-range")
  public List<Book> findByPriceRange(@RequestParam double low, @RequestParam double high) {
    return bookRepository.findByPriceBetween(low, high);
  }

  @GetMapping("/by-year-range")
  public List<Book> findByYearRange(@RequestParam int from, @RequestParam int to) {
    return bookRepository.findByYearBetween(from, to);
  }

  @GetMapping("/cheap")
  public List<Book> findCheap(@RequestParam(defaultValue = "15") double maxPrice) {
    return bookRepository.findByPriceLessThan(maxPrice);
  }

  @GetMapping("/custom-search")
  public List<Book> customSearch(@RequestParam String title, @RequestParam String author) {
    return bookRepository.findByTitleAndAuthorCustom(title, author);
  }

  @GetMapping("/category-price")
  public List<Book> findByCategoryAndPrice(@RequestParam String category,
      @RequestParam double low, @RequestParam double high) {
    return bookRepository.findByCategoryAndPriceRange(category, low, high);
  }

  @GetMapping("/count-category")
  public Map<String, Object> countByCategory(@RequestParam String category) {
    return Map.of("category", category, "count", bookRepository.countByCategoryCustom(category));
  }

  @GetMapping("/highlight")
  public HighlightPage<Book> searchWithHighlights(@RequestParam String q) {
    var query = new SimpleQuery(Criteria.where("description_t").contains(q));
    query.setPageable(PageRequest.of(0, 10));
    query.setHighlightOptions(new HighlightOptions()
        .addField("description_t")
        .addField("title_t")
        .preTag("<em>")
        .postTag("</em>")
        .snippets(2)
        .fragsize(200));
    return solrTemplate.queryForHighlightPage(COLLECTION, query, Book.class);
  }

  @GetMapping("/facets")
  public FacetPage<Book> searchWithFacets(
      @RequestParam(defaultValue = "*") String q) {
    var criteria = q.equals("*")
        ? Criteria.where("*").expression("*")
        : Criteria.where("description_t").contains(q);
    var query = new SimpleQuery(criteria);
    query.setPageable(PageRequest.of(0, 10));
    query.setFacetOptions(new FacetOptions()
        .addFacetOnField("categories_ss")
        .addFacetOnField("author_s")
        .minCount(1)
        .limit(20));
    return solrTemplate.queryForFacetPage(COLLECTION, query, Book.class);
  }

  @GetMapping("/cursor")
  public CursorResult<Book> cursorPage(
      @RequestParam(defaultValue = "*") String cursorMark,
      @RequestParam(defaultValue = "10") int pageSize) {
    var query = new SimpleQuery(Criteria.where("*").expression("*"));
    query.setCursorMark(cursorMark);
    query.setPageable(PageRequest.of(0, pageSize, Sort.by("id").ascending()));
    return solrTemplate.queryWithCursor(COLLECTION, query, Book.class);
  }

  @GetMapping("/nearby")
  public List<Book> findNearby(
      @RequestParam double lat, @RequestParam double lon,
      @RequestParam(defaultValue = "50") double radiusKm) {
    var query = new SimpleQuery(
        Criteria.where("location").near(
            new GeoPoint(lat, lon),
            GeoDistance.kilometers(radiusKm)));
    query.setPageable(PageRequest.of(0, 20));
    return solrTemplate.queryForPage(COLLECTION, query, Book.class).getContent();
  }

  @GetMapping("/within")
  public List<Book> findWithinBounds(
      @RequestParam double lat, @RequestParam double lon,
      @RequestParam(defaultValue = "100") double radiusKm) {
    var query = new SimpleQuery(
        Criteria.where("location").within(
            new GeoPoint(lat, lon),
            GeoDistance.kilometers(radiusKm)));
    query.setPageable(PageRequest.of(0, 20));
    return solrTemplate.queryForPage(COLLECTION, query, Book.class).getContent();
  }

  @GetMapping("/stats")
  public Map<String, Object> stats() {
    return Map.of(
        "totalBooks", bookRepository.count(),
        "inStock", bookRepository.findByInStock(true).size(),
        "outOfStock", bookRepository.findByInStock(false).size());
  }
}
