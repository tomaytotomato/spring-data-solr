package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.SolrTemplate;
import com.tomaytotomato.data.solr.mapping.SolrDocumentResolver;
import com.tomaytotomato.data.solr.query.Criteria;
import com.tomaytotomato.data.solr.query.SimpleQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Default {@link SolrRepository} implementation.
 *
 * <p>Delegates all persistence operations to a {@link SolrTemplate}, using the collection name
 * derived from the {@link com.tomaytotomato.data.solr.mapping.SolrDocument} annotation on the
 * entity class. This class is instantiated by the repository infrastructure and is not normally
 * used directly by application code.
 *
 * <p>All write operations honour the {@link com.tomaytotomato.data.solr.CommitMode} configured on
 * the underlying {@link SolrTemplate}. When using {@link com.tomaytotomato.data.solr.CommitMode#NONE}
 * (the default) callers must issue an explicit commit through {@link SolrTemplate#commit(String)}
 * before writes are visible to searchers.
 *
 * @param <T> the domain type managed by this repository
 * @since 0.1.0
 */
public class SimpleSolrRepository<T> implements SolrRepository<T> {

  /**
   * Default maximum number of documents returned by {@link #findAll()} and {@link #findAll(Sort)},
   * matching Solr's default {@code maxResultWindow} collection setting of 10,000.
   *
   * <p>Solr enforces a result-window limit via the {@code maxResultWindow} collection setting
   * (default 10,000). Requesting more rows than this limit throws
   * {@code "Result window is too large"}.
   *
   * <p>For collections that may exceed this limit use {@link #findAll(Pageable)} with an explicit
   * page size, or use cursor-based deep pagination via
   * {@link com.tomaytotomato.data.solr.SolrOperations#queryWithCursor}.
   */
  static final int DEFAULT_MAX_ROWS = 10_000;

  private final SolrTemplate solrTemplate;
  private final Class<T> entityClass;
  private final String collection;
  private final SolrEntityInformation<T> entityInformation;

  /**
   * Creates a new {@link SimpleSolrRepository} for the given entity class, resolving the
   * collection name via the supplied {@link SolrDocumentResolver} so that
   * {@code ${placeholder}} values in {@link com.tomaytotomato.data.solr.mapping.SolrDocument#collection()}
   * are expanded against the Spring {@link org.springframework.core.env.Environment}.
   *
   * @param solrTemplate the template to delegate persistence operations to
   * @param entityClass  the domain class managed by this repository
   * @param resolver     resolver used to determine the collection name
   */
  public SimpleSolrRepository(SolrTemplate solrTemplate, Class<T> entityClass,
      SolrDocumentResolver resolver) {
    this.solrTemplate = solrTemplate;
    this.entityClass = entityClass;
    this.collection = resolver.resolve(entityClass);
    this.entityInformation = new SolrEntityInformation<>(entityClass, resolver);
  }

  /**
   * Creates a new {@link SimpleSolrRepository} for the given entity class without placeholder
   * resolution.
   *
   * <p>Retained for backward compatibility. Prefer the
   * {@link #SimpleSolrRepository(SolrTemplate, Class, SolrDocumentResolver)} constructor when a
   * Spring {@link org.springframework.core.env.Environment} is available.
   *
   * @param solrTemplate the template to delegate persistence operations to
   * @param entityClass the domain class managed by this repository
   */
  public SimpleSolrRepository(SolrTemplate solrTemplate, Class<T> entityClass) {
    this.solrTemplate = solrTemplate;
    this.entityClass = entityClass;
    this.collection = SolrDocumentResolver.resolveCollection(entityClass);
    this.entityInformation = new SolrEntityInformation<>(entityClass);
  }

  /**
   * Saves the given entity to the collection.
   *
   * @param <S> the entity type
   * @param entity the entity to save
   * @return the saved entity
   */
  @Override
  public <S extends T> S save(S entity) {
    return solrTemplate.save(collection, entity);
  }

  /**
   * Saves all given entities to the collection in a single batch request.
   *
   * @param <S> the entity type
   * @param entities the entities to save
   * @return the saved entities
   */
  @Override
  public <S extends T> Iterable<S> saveAll(Iterable<S> entities) {
    var list = new ArrayList<S>();
    entities.forEach(list::add);
    return solrTemplate.saveAll(collection, list);
  }

  /**
   * Retrieves a document by its unique ID.
   *
   * @param id the document ID
   * @return an {@link Optional} containing the entity, or empty if not found
   */
  @Override
  public Optional<T> findById(String id) {
    return solrTemplate.findById(collection, id, entityClass);
  }

  /**
   * Returns {@code true} if a document with the given ID exists in the collection.
   *
   * @param id the document ID to check
   * @return {@code true} if a matching document exists
   */
  @Override
  public boolean existsById(String id) {
    return findById(id).isPresent();
  }

  /**
   * Returns up to {@value #DEFAULT_MAX_ROWS} documents from the collection.
   *
   * <p>Solr's {@code maxResultWindow} setting (default 10,000) caps the maximum rows any single
   * query can request. This method applies a safe default of {@value #DEFAULT_MAX_ROWS} rows.
   * For complete iteration over large collections use {@link #findAll(Pageable)} with an explicit
   * page size, or cursor-based pagination via
   * {@link com.tomaytotomato.data.solr.SolrOperations#queryWithCursor}.
   */
  @Override
  public Iterable<T> findAll() {
    var query = new SolrQuery("*:*");
    query.setRows(DEFAULT_MAX_ROWS);
    return solrTemplate.query(collection, query, entityClass);
  }

  /**
   * Retrieves all documents whose IDs are contained in the given iterable.
   *
   * <p>IDs are combined into a single Solr {@code id:(a OR b OR ...)} query. Returns an empty
   * list when the iterable is empty.
   *
   * @param ids the document IDs to retrieve
   * @return a list of matching entities; never {@code null}
   */
  @Override
  public Iterable<T> findAllById(Iterable<String> ids) {
    var idList = StreamSupport.stream(ids.spliterator(), false)
        .map(ClientUtils::escapeQueryChars)
        .collect(Collectors.toList());
    if (idList.isEmpty()) {
      return List.of();
    }
    var idQuery = "id:(" + String.join(" OR ", idList) + ")";
    return solrTemplate.query(collection, new SolrQuery(idQuery), entityClass);
  }

  /**
   * Returns the total number of documents in the collection.
   *
   * @return the document count
   */
  @Override
  public long count() {
    return solrTemplate.count(collection, new SimpleQuery(Criteria.matchAll()));
  }

  /**
   * Deletes the document with the given ID from the collection.
   *
   * @param id the ID of the document to delete
   */
  @Override
  public void deleteById(String id) {
    solrTemplate.deleteById(collection, id);
  }

  /**
   * Deletes the given entity from the collection, resolving its ID via
   * {@link SolrEntityInformation}.
   *
   * @param entity the entity to delete; ignored if its ID is {@code null}
   */
  @Override
  public void delete(T entity) {
    var id = entityInformation.getId(entity);
    if (id != null) {
      deleteById(id);
    }
  }

  /**
   * Deletes all documents whose IDs are contained in the given iterable.
   *
   * @param ids the IDs of the documents to delete
   */
  @Override
  public void deleteAllById(Iterable<? extends String> ids) {
    ids.forEach(this::deleteById);
  }

  /**
   * Deletes all given entities from the collection.
   *
   * @param entities the entities to delete
   */
  @Override
  public void deleteAll(Iterable<? extends T> entities) {
    entities.forEach(this::delete);
  }

  /**
   * Deletes all documents from the collection and issues a hard commit to make the change
   * immediately visible.
   */
  @Override
  public void deleteAll() {
    solrTemplate.deleteByQuery(collection, "*:*");
    solrTemplate.commit(collection);
  }

  /**
   * Returns a page of documents from the collection according to the given pageable.
   *
   * @param pageable pagination and sort parameters
   * @return a {@link Page} of results for the requested page
   */
  @Override
  public Page<T> findAll(Pageable pageable) {
    var query = new SimpleQuery(Criteria.matchAll(), pageable);
    return solrTemplate.queryForPage(collection, query, entityClass);
  }

  /**
   * Returns up to {@value #DEFAULT_MAX_ROWS} documents from the collection, ordered by the given
   * sort specification.
   *
   * <p>Solr's {@code maxResultWindow} setting (default 10,000) caps the maximum rows any single
   * query can request. This method applies a safe default of {@value #DEFAULT_MAX_ROWS} rows.
   * For complete iteration over large collections use {@link #findAll(Pageable)} with an explicit
   * page size, or cursor-based pagination via
   * {@link com.tomaytotomato.data.solr.SolrOperations#queryWithCursor}.
   */
  @Override
  public Iterable<T> findAll(Sort sort) {
    var query = new SimpleQuery(Criteria.matchAll());
    query.setSort(sort);
    var solrQuery = query.toSolrQuery();
    solrQuery.setRows(DEFAULT_MAX_ROWS);
    return solrTemplate.query(collection, solrQuery, entityClass);
  }
}
