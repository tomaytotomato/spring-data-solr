package com.tomaytotomato.data.solr;

import com.tomaytotomato.data.solr.query.SimpleQuery;
import com.tomaytotomato.data.solr.query.StreamingExpression;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.springframework.data.domain.Pageable;

public interface SolrOperations {

  <T> T save(String collection, T entity);

  <T> List<T> saveAll(String collection, Collection<T> entities);

  void savePartialUpdate(String collection, PartialUpdate update);

  <T> Optional<T> findById(String collection, String id, Class<T> type);

  <T> List<T> query(String collection, SolrQuery query, Class<T> type);

  <T> SolrPage<T> queryForPage(String collection, SimpleQuery query, Class<T> type);

  <T> HighlightPage<T> queryForHighlightPage(String collection, SimpleQuery query, Class<T> type);

  <T> FacetPage<T> queryForFacetPage(String collection, SimpleQuery query, Class<T> type);

  long count(String collection, SimpleQuery query);

  long count(String collection, SolrQuery query);

  void deleteById(String collection, String id);

  void deleteByQuery(String collection, String query);

  void commit(String collection);

  void softCommit(String collection);

  // @SolrDocument-aware convenience methods

  <T> T save(T entity);

  <T> Optional<T> findById(String id, Class<T> type);

  /**
   * Executes a paged query, resolving the collection from the {@code @SolrDocument} annotation on
   * {@code type}. The {@code pageable} is applied to the query before execution, controlling the
   * page size and offset sent to Solr.
   */
  <T> SolrPage<T> queryForPage(SimpleQuery query, Class<T> type, Pageable pageable);

  <T> CursorResult<T> queryWithCursor(String collection, SimpleQuery query, Class<T> type);

  List<Map<String, Object>> stream(String collection, StreamingExpression expression);
}
