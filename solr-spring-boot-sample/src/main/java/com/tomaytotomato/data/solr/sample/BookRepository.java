package com.tomaytotomato.data.solr.sample;

import com.tomaytotomato.data.solr.repository.Query;
import com.tomaytotomato.data.solr.repository.SolrRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookRepository extends SolrRepository<Book> {

  List<Book> findByAuthor(String author);

  List<Book> findByTitleContaining(String title);

  List<Book> findByTitleStartingWith(String prefix);

  List<Book> findByInStock(boolean inStock);

  List<Book> findByPriceGreaterThan(double price);

  List<Book> findByPriceLessThan(double price);

  List<Book> findByPriceBetween(double low, double high);

  List<Book> findByRatingGreaterThanEqual(double rating);

  List<Book> findByYearBetween(int from, int to);

  Page<Book> findByAuthorAndYearGreaterThan(String author, int year, Pageable pageable);

  long countByAuthor(String author);

  boolean existsByTitle(String title);

  @Query("title_t:?0 AND author_s:?1")
  List<Book> findByTitleAndAuthorCustom(String title, String author);

  @Query("categories_ss:?0 AND price_d:[?1 TO ?2]")
  List<Book> findByCategoryAndPriceRange(String category, double low, double high);

  @Query(value = "categories_ss:?0", count = true)
  long countByCategoryCustom(String category);
}
