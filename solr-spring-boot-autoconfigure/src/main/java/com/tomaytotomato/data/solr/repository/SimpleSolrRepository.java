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

public class SimpleSolrRepository<T, ID> implements SolrRepository<T, ID> {

  private final SolrTemplate solrTemplate;
  private final Class<T> entityClass;
  private final String collection;
  private final SolrEntityInformation<T, ID> entityInformation;

  @SuppressWarnings("unchecked")
  public SimpleSolrRepository(SolrTemplate solrTemplate, Class<T> entityClass) {
    this.solrTemplate = solrTemplate;
    this.entityClass = entityClass;
    this.collection = SolrDocumentResolver.resolveCollection(entityClass);
    this.entityInformation = new SolrEntityInformation<>(entityClass, (Class<ID>) String.class);
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
  public Optional<T> findById(ID id) {
    return solrTemplate.findById(collection, id.toString(), entityClass);
  }

  @Override
  public boolean existsById(ID id) {
    return findById(id).isPresent();
  }

  @Override
  public Iterable<T> findAll() {
    var query = new SolrQuery("*:*");
    query.setRows(Integer.MAX_VALUE);
    return solrTemplate.query(collection, query, entityClass);
  }

  @Override
  public Iterable<T> findAllById(Iterable<ID> ids) {
    var idList = StreamSupport.stream(ids.spliterator(), false)
        .map(Object::toString)
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
  public void deleteById(ID id) {
    solrTemplate.deleteById(collection, id.toString());
  }

  @Override
  public void delete(T entity) {
    var id = entityInformation.getId(entity);
    if (id != null) {
      deleteById(id);
    }
  }

  @Override
  public void deleteAllById(Iterable<? extends ID> ids) {
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

  @Override
  public Iterable<T> findAll(Sort sort) {
    var query = new SimpleQuery(Criteria.where("*").expression("*"));
    query.setSort(sort);
    var solrQuery = query.toSolrQuery();
    solrQuery.setRows(Integer.MAX_VALUE);
    return solrTemplate.query(collection, solrQuery, entityClass);
  }
}
