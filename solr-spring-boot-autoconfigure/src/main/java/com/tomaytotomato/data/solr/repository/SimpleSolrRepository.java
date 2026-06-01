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

  public SimpleSolrRepository(SolrTemplate solrTemplate, Class<T> entityClass) {
    this.solrTemplate = solrTemplate;
    this.entityClass = entityClass;
    this.collection = SolrDocumentResolver.resolveCollection(entityClass);
    this.entityInformation = new SolrEntityInformation<>(entityClass);
  }

  @Override
  public <S extends T> S save(S entity) {
    return solrTemplate.save(collection, entity);
  }

  @Override
  public <S extends T> Iterable<S> saveAll(Iterable<S> entities) {
    var list = new ArrayList<S>();
    entities.forEach(list::add);
    return solrTemplate.saveAll(collection, list);
  }

  @Override
  public Optional<T> findById(String id) {
    return solrTemplate.findById(collection, id, entityClass);
  }

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

  @Override
  public long count() {
    return solrTemplate.count(collection, new SimpleQuery(Criteria.where("*").expression("*")));
  }

  @Override
  public void deleteById(String id) {
    solrTemplate.deleteById(collection, id);
  }

  @Override
  public void delete(T entity) {
    var id = entityInformation.getId(entity);
    if (id != null) {
      deleteById(id);
    }
  }

  @Override
  public void deleteAllById(Iterable<? extends String> ids) {
    ids.forEach(this::deleteById);
  }

  @Override
  public void deleteAll(Iterable<? extends T> entities) {
    entities.forEach(this::delete);
  }

  @Override
  public void deleteAll() {
    solrTemplate.deleteByQuery(collection, "*:*");
    solrTemplate.commit(collection);
  }

  @Override
  public Page<T> findAll(Pageable pageable) {
    var query = new SimpleQuery(Criteria.where("*").expression("*"), pageable);
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
    var query = new SimpleQuery(Criteria.where("*").expression("*"));
    query.setSort(sort);
    var solrQuery = query.toSolrQuery();
    solrQuery.setRows(DEFAULT_MAX_ROWS);
    return solrTemplate.query(collection, solrQuery, entityClass);
  }
}
