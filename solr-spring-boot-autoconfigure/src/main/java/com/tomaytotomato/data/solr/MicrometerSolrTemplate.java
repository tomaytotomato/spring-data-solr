package com.tomaytotomato.data.solr;

import com.tomaytotomato.data.solr.query.SimpleQuery;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Collection;
import java.util.List;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.springframework.core.env.Environment;

public class MicrometerSolrTemplate extends SolrTemplate {

  private static final String METRIC_NAME = "solr.operations";

  private final MeterRegistry meterRegistry;

  public MicrometerSolrTemplate(SolrClient solrClient, CommitMode commitMode,
      Environment environment, MeterRegistry meterRegistry) {
    super(solrClient, commitMode, environment);
    this.meterRegistry = meterRegistry;
  }

  @Override
  public <T> T save(String collection, T entity) {
    return timer("save", collection).record(() -> super.save(collection, entity));
  }

  @Override
  public <T> List<T> saveAll(String collection, Collection<T> entities) {
    return timer("saveAll", collection).record(() -> super.saveAll(collection, entities));
  }

  @Override
  public <T> List<T> query(String collection, SolrQuery query, Class<T> type) {
    return timer("query", collection).record(() -> super.query(collection, query, type));
  }

  @Override
  public <T> SolrPage<T> queryForPage(String collection, SimpleQuery query, Class<T> type) {
    return timer("queryForPage", collection).record(() -> super.queryForPage(collection, query, type));
  }

  @Override
  public <T> HighlightPage<T> queryForHighlightPage(String collection, SimpleQuery query, Class<T> type) {
    return timer("queryForHighlightPage", collection)
        .record(() -> super.queryForHighlightPage(collection, query, type));
  }

  @Override
  public <T> FacetPage<T> queryForFacetPage(String collection, SimpleQuery query, Class<T> type) {
    return timer("queryForFacetPage", collection)
        .record(() -> super.queryForFacetPage(collection, query, type));
  }

  @Override
  public long count(String collection, SimpleQuery query) {
    return timer("count", collection).record(() -> super.count(collection, query));
  }

  @Override
  public long count(String collection, SolrQuery query) {
    return timer("count", collection).record(() -> super.count(collection, query));
  }

  @Override
  public void deleteById(String collection, String id) {
    timer("deleteById", collection).record(() -> super.deleteById(collection, id));
  }

  @Override
  public void deleteByQuery(String collection, String query) {
    timer("deleteByQuery", collection).record(() -> super.deleteByQuery(collection, query));
  }

  @Override
  public void savePartialUpdate(String collection, PartialUpdate update) {
    timer("partial-update", collection).record(() -> super.savePartialUpdate(collection, update));
  }

  @Override
  public void commit(String collection) {
    timer("commit", collection).record(() -> super.commit(collection));
  }

  @Override
  public void softCommit(String collection) {
    timer("soft-commit", collection).record(() -> super.softCommit(collection));
  }

  @Override
  public <T> CursorResult<T> queryWithCursor(String collection, SimpleQuery query, Class<T> type) {
    return timer("cursor-query", collection).record(() -> super.queryWithCursor(collection, query, type));
  }

  private Timer timer(String operation, String collection) {
    return Timer.builder(METRIC_NAME)
        .tag("operation", operation)
        .tag("collection", collection)
        .register(meterRegistry);
  }
}
