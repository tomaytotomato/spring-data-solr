package com.tomaytotomato.data.solr;

import com.tomaytotomato.data.solr.query.SimpleQuery;
import com.tomaytotomato.data.solr.query.StreamingExpression;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.springframework.data.domain.Pageable;

/**
 * Central interface for Solr data access operations.
 *
 * <p>Provides collection-aware CRUD, paged queries, highlight and facet queries, cursor-based
 * deep pagination, streaming expressions, and explicit commit control. Two flavours of most
 * operations are available:
 * <ul>
 *   <li><strong>Collection-explicit</strong> — the caller supplies the Solr collection name.</li>
 *   <li><strong>{@code @SolrEntity}-aware</strong> — the collection name is resolved
 *       automatically from the {@link com.tomaytotomato.data.solr.mapping.SolrEntity} annotation
 *       on the entity class.</li>
 * </ul>
 *
 * <p>The primary implementation is {@link SolrTemplate}.
 *
 * @since 0.1.0
 */
public interface SolrOperations {

  /**
   * Saves the given entity to the specified collection.
   *
   * @param <T> the entity type
   * @param collection the target Solr collection
   * @param entity the entity to save
   * @return the saved entity
   */
  <T> T save(String collection, T entity);

  /**
   * Saves all given entities to the specified collection in a single batch request.
   *
   * @param <T> the entity type
   * @param collection the target Solr collection
   * @param entities the entities to save
   * @return the saved entities
   */
  <T> List<T> saveAll(String collection, Collection<T> entities);

  /**
   * Applies a partial update to an existing document in the specified collection.
   *
   * @param collection the target Solr collection
   * @param update the partial update descriptor
   */
  void savePartialUpdate(String collection, PartialUpdate update);

  /**
   * Retrieves a single document by its unique ID from the specified collection.
   *
   * @param <T> the entity type
   * @param collection the Solr collection to search
   * @param id the document ID
   * @param type the target entity class
   * @return an {@link Optional} containing the entity, or empty if not found
   */
  <T> Optional<T> findById(String collection, String id, Class<T> type);

  /**
   * Executes a raw SolrJ query against the specified collection and returns all matched documents.
   *
   * @param <T> the entity type
   * @param collection the Solr collection to query
   * @param query the SolrJ query to execute
   * @param type the target entity class
   * @return a list of matched entities, possibly empty
   */
  <T> List<T> query(String collection, SolrQuery query, Class<T> type);

  /**
   * Executes a paged query against the specified collection.
   *
   * @param <T> the entity type
   * @param collection the Solr collection to query
   * @param query the query including pagination settings
   * @param type the target entity class
   * @return a {@link SolrPage} containing the results and total hit count
   */
  <T> SolrPage<T> queryForPage(String collection, SimpleQuery query, Class<T> type);

  /**
   * Executes a query with Solr highlighting against the specified collection.
   *
   * <p>The query must include highlight options (pre/post tags, fields, etc.) via
   * {@link com.tomaytotomato.data.solr.query.SimpleQuery#setHighlightOptions}.
   *
   * @param <T> the entity type
   * @param collection the Solr collection to query
   * @param query the query including highlight configuration
   * @param type the target entity class
   * @return a {@link HighlightPage} containing results and per-document highlight snippets
   */
  <T> HighlightPage<T> queryForHighlightPage(String collection, SimpleQuery query, Class<T> type);

  /**
   * Executes a faceted query against the specified collection.
   *
   * <p>The query must include facet options (fields, queries, minCount, etc.) via
   * {@link com.tomaytotomato.data.solr.query.SimpleQuery#setFacetOptions}.
   *
   * @param <T> the entity type
   * @param collection the Solr collection to query
   * @param query the query including facet configuration
   * @param type the target entity class
   * @return a {@link FacetPage} containing results and facet counts
   */
  <T> FacetPage<T> queryForFacetPage(String collection, SimpleQuery query, Class<T> type);

  /**
   * Counts documents matching the given query in the specified collection.
   *
   * @param collection the Solr collection to query
   * @param query the query whose matches will be counted
   * @return the number of matching documents
   */
  long count(String collection, SimpleQuery query);

  /**
   * Counts documents matching the given raw SolrJ query in the specified collection.
   *
   * @param collection the Solr collection to query
   * @param query the SolrJ query whose matches will be counted
   * @return the number of matching documents
   */
  long count(String collection, SolrQuery query);

  /**
   * Deletes a document by its unique ID from the specified collection.
   *
   * @param collection the Solr collection to modify
   * @param id the ID of the document to delete
   */
  void deleteById(String collection, String id);

  /**
   * Deletes all documents matching the given query string from the specified collection.
   *
   * @param collection the Solr collection to modify
   * @param query the Solr query string identifying documents to delete
   */
  void deleteByQuery(String collection, String query);

  /**
   * Issues a hard commit to the specified collection, making all pending changes visible to
   * searchers and durable on disk.
   *
   * @param collection the Solr collection to commit
   */
  void commit(String collection);

  /**
   * Issues a soft commit to the specified collection, making pending changes visible to searchers
   * without a full flush to disk.
   *
   * @param collection the Solr collection to soft-commit
   */
  void softCommit(String collection);

  // @SolrEntity-aware convenience methods

  /**
   * Saves the given entity, resolving the target collection from the
   * {@link com.tomaytotomato.data.solr.mapping.SolrEntity} annotation on the entity class.
   *
   * @param <T> the entity type
   * @param entity the entity to save
   * @return the saved entity
   */
  <T> T save(T entity);

  /**
   * Retrieves a single document by its unique ID, resolving the collection from the
   * {@link com.tomaytotomato.data.solr.mapping.SolrEntity} annotation on {@code type}.
   *
   * @param <T> the entity type
   * @param id the document ID
   * @param type the target entity class
   * @return an {@link Optional} containing the entity, or empty if not found
   */
  <T> Optional<T> findById(String id, Class<T> type);

  /**
   * Executes a paged query, resolving the collection from the {@code @SolrEntity} annotation on
   * {@code type}. The {@code pageable} is applied to the query before execution, controlling the
   * page size and offset sent to Solr.
   *
   * @param <T> the entity type
   * @param query the query to execute
   * @param type the target entity class; its {@code @SolrEntity} annotation provides the collection name
   * @param pageable pagination and sort parameters
   * @return a {@link SolrPage} containing the results and total hit count
   */
  <T> SolrPage<T> queryForPage(SimpleQuery query, Class<T> type, Pageable pageable);

  /**
   * Executes a cursor-based deep pagination query against the specified collection.
   *
   * <p>Set {@link SimpleQuery#setCursorMark(String)} to {@code "*"} for the first request, then
   * pass {@link CursorResult#cursorMark()} from the previous response for each subsequent page.
   * The query sort must include the collection's uniqueKey field.
   *
   * @param <T> the entity type
   * @param collection the Solr collection to query
   * @param query the query with cursor mark set
   * @param type the target entity class
   * @return a {@link CursorResult} containing the current page of results and the next cursor mark
   */
  <T> CursorResult<T> queryWithCursor(String collection, SimpleQuery query, Class<T> type);

  /**
   * Executes a Solr streaming expression against the specified collection.
   *
   * <p>Results are returned as a list of plain maps, one entry per tuple returned by the
   * streaming handler. The EOF tuple is excluded from the result.
   *
   * @param collection the Solr collection to stream from
   * @param expression the streaming expression to execute
   * @return a list of result tuples, each represented as a {@code Map<String, Object>}
   */
  List<Map<String, Object>> stream(String collection, StreamingExpression expression);
}
