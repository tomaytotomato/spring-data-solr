package com.tomaytotomato.data.solr;

import com.tomaytotomato.data.solr.mapping.SolrMappingContext;
import com.tomaytotomato.data.solr.mapping.SolrMappingConverter;
import com.tomaytotomato.data.solr.query.SimpleQuery;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.springframework.core.env.Environment;

public class MicrometerSolrTemplate extends SolrTemplate {

  private static final String OBSERVATION_NAME = "solr.operation";

  private final ObservationRegistry observationRegistry;

  /**
   * Primary constructor. Creates a {@link MicrometerSolrTemplate} with full configuration.
   *
   * @param solrClient          the SolrJ client to delegate to
   * @param commitMode          controls whether writes are automatically committed
   * @param environment         the Spring environment for property resolution; may be {@code null}
   * @param observationRegistry the Micrometer Observation registry for operation tracing
   * @param mappingContext      the mapping context for field-name resolution; may be {@code null}
   * @param mappingConverter    the converter for custom field type conversions; must not be
   *                            {@code null}
   */
  public MicrometerSolrTemplate(SolrClient solrClient, CommitMode commitMode,
      Environment environment, ObservationRegistry observationRegistry,
      SolrMappingContext mappingContext, SolrMappingConverter mappingConverter) {
    super(solrClient, commitMode, environment, mappingContext, mappingConverter);
    this.observationRegistry = observationRegistry;
  }

  /**
   * Creates a {@link MicrometerSolrTemplate} without a custom converter. Retained for backward
   * compatibility.
   *
   * @param solrClient          the SolrJ client to delegate to
   * @param commitMode          controls whether writes are automatically committed
   * @param environment         the Spring environment for property resolution; may be {@code null}
   * @param observationRegistry the Micrometer Observation registry for operation tracing
   * @param mappingContext      the mapping context for field-name resolution; may be {@code null}
   */
  public MicrometerSolrTemplate(SolrClient solrClient, CommitMode commitMode,
      Environment environment, ObservationRegistry observationRegistry,
      SolrMappingContext mappingContext) {
    this(solrClient, commitMode, environment, observationRegistry, mappingContext,
        new SolrMappingConverter());
  }

  /**
   * Creates a {@link MicrometerSolrTemplate} without a mapping context or custom converter.
   * Retained for backward compatibility.
   *
   * @param solrClient          the SolrJ client to delegate to
   * @param commitMode          controls whether writes are automatically committed
   * @param environment         the Spring environment for property resolution; may be {@code null}
   * @param observationRegistry the Micrometer Observation registry for operation tracing
   */
  public MicrometerSolrTemplate(SolrClient solrClient, CommitMode commitMode,
      Environment environment, ObservationRegistry observationRegistry) {
    this(solrClient, commitMode, environment, observationRegistry, null, new SolrMappingConverter());
  }

  @Override
  public <T> T save(String collection, T entity) {
    return observe("save", collection, () -> super.save(collection, entity));
  }

  @Override
  public <T> List<T> saveAll(String collection, Collection<T> entities) {
    return observe("saveAll", collection, () -> super.saveAll(collection, entities));
  }

  @Override
  public <T> List<T> query(String collection, SolrQuery query, Class<T> type) {
    return observe("query", collection, () -> super.query(collection, query, type));
  }

  @Override
  public <T> SolrPage<T> queryForPage(String collection, SimpleQuery query, Class<T> type) {
    return observe("queryForPage", collection, () -> super.queryForPage(collection, query, type));
  }

  @Override
  public <T> HighlightPage<T> queryForHighlightPage(String collection, SimpleQuery query, Class<T> type) {
    return observe("queryForHighlightPage", collection,
        () -> super.queryForHighlightPage(collection, query, type));
  }

  @Override
  public <T> FacetPage<T> queryForFacetPage(String collection, SimpleQuery query, Class<T> type) {
    return observe("queryForFacetPage", collection,
        () -> super.queryForFacetPage(collection, query, type));
  }

  @Override
  public long count(String collection, SimpleQuery query) {
    return observe("count", collection, () -> super.count(collection, query));
  }

  @Override
  public long count(String collection, SolrQuery query) {
    return observe("count", collection, () -> super.count(collection, query));
  }

  @Override
  public void deleteById(String collection, String id) {
    observeVoid("deleteById", collection, () -> super.deleteById(collection, id));
  }

  @Override
  public void deleteByQuery(String collection, String query) {
    observeVoid("deleteByQuery", collection, () -> super.deleteByQuery(collection, query));
  }

  @Override
  public void savePartialUpdate(String collection, PartialUpdate update) {
    observeVoid("partial-update", collection, () -> super.savePartialUpdate(collection, update));
  }

  @Override
  public void commit(String collection) {
    observeVoid("commit", collection, () -> super.commit(collection));
  }

  @Override
  public void softCommit(String collection) {
    observeVoid("soft-commit", collection, () -> super.softCommit(collection));
  }

  @Override
  public <T> CursorResult<T> queryWithCursor(String collection, SimpleQuery query, Class<T> type) {
    return observe("cursor-query", collection, () -> super.queryWithCursor(collection, query, type));
  }

  private <T> T observe(String operation, String collection, Supplier<T> callable) {
    return Observation.createNotStarted(OBSERVATION_NAME, observationRegistry)
        .lowCardinalityKeyValue("operation", operation)
        .lowCardinalityKeyValue("collection", collection)
        .observe(callable);
  }

  private void observeVoid(String operation, String collection, Runnable action) {
    Observation.createNotStarted(OBSERVATION_NAME, observationRegistry)
        .lowCardinalityKeyValue("operation", operation)
        .lowCardinalityKeyValue("collection", collection)
        .observe(action);
  }
}
