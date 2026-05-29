package com.tomaytotomato.data.solr;

import com.tomaytotomato.data.solr.mapping.SolrDocumentReader;
import com.tomaytotomato.data.solr.mapping.SolrDocumentResolver;
import com.tomaytotomato.data.solr.mapping.SolrDocumentWriter;
import com.tomaytotomato.data.solr.query.FacetFieldEntry;
import com.tomaytotomato.data.solr.query.FacetQueryEntry;
import com.tomaytotomato.data.solr.query.HighlightEntry;
import com.tomaytotomato.data.solr.query.SimpleQuery;
import com.tomaytotomato.data.solr.query.StreamingExpression;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.GenericSolrRequest;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Pageable;

public class SolrTemplate implements SolrOperations {

  private final SolrClient solrClient;
  private final CommitMode commitMode;
  private final Environment environment;

  public SolrTemplate(SolrClient solrClient) {
    this(solrClient, CommitMode.NONE, null);
  }

  public SolrTemplate(SolrClient solrClient, CommitMode commitMode) {
    this(solrClient, commitMode, null);
  }

  public SolrTemplate(SolrClient solrClient, CommitMode commitMode, Environment environment) {
    this.solrClient = solrClient;
    this.commitMode = commitMode;
    this.environment = environment;
  }

  @Override
  public <T> T save(String collection, T entity) {
    try {
      var writer = new SolrDocumentWriter<T>();
      solrClient.add(collection, writer.convert(entity));
      commitIfImmediate(collection);
      return entity;
    } catch (IOException | SolrServerException e) {
      throw new SolrException("Failed to save entity to collection: " + collection, e);
    }
  }

  @Override
  public <T> T save(T entity) {
    var collection = SolrDocumentResolver.resolveCollection(entity.getClass(), environment);
    return save(collection, entity);
  }

  @Override
  public <T> List<T> saveAll(String collection, Collection<T> entities) {
    try {
      var writer = new SolrDocumentWriter<T>();
      var docs = entities.stream().map(writer::convert).toList();
      solrClient.add(collection, docs);
      commitIfImmediate(collection);
      return new ArrayList<>(entities);
    } catch (IOException | SolrServerException e) {
      throw new SolrException("Failed to save entities to collection: " + collection, e);
    }
  }

  @Override
  public void savePartialUpdate(String collection, PartialUpdate update) {
    try {
      solrClient.add(collection, update.toSolrInputDocument());
      commitIfImmediate(collection);
    } catch (IOException | SolrServerException e) {
      throw new SolrException("Failed to save partial update to collection: " + collection, e);
    }
  }

  @Override
  public <T> Optional<T> findById(String collection, String id, Class<T> type) {
    try {
      var query = new SolrQuery("id:" + ClientUtils.escapeQueryChars(id));
      query.setRows(1);
      var response = solrClient.query(collection, query);
      var beans = mapDocuments(response.getResults(), type);
      return beans.isEmpty() ? Optional.empty() : Optional.of(beans.getFirst());
    } catch (IOException | SolrServerException e) {
      throw new SolrException(
          "Failed to find entity by id '%s' in collection: %s".formatted(id, collection), e);
    }
  }

  @Override
  public <T> Optional<T> findById(String id, Class<T> type) {
    var collection = SolrDocumentResolver.resolveCollection(type, environment);
    return findById(collection, id, type);
  }

  @Override
  public <T> List<T> query(String collection, SolrQuery query, Class<T> type) {
    try {
      QueryResponse response = solrClient.query(collection, query);
      return mapDocuments(response.getResults(), type);
    } catch (IOException | SolrServerException e) {
      throw new SolrException("Failed to query collection: " + collection, e);
    }
  }

  @Override
  public <T> SolrPage<T> queryForPage(String collection, SimpleQuery query, Class<T> type) {
    try {
      var solrQuery = query.toSolrQuery();
      QueryResponse response = solrClient.query(collection, solrQuery);
      var docs = response.getResults();
      var beans = mapDocuments(docs, type);
      long numFound = docs.getNumFound();
      Float maxScore = docs.getMaxScore();

      int start = solrQuery.getStart() != null ? solrQuery.getStart() : 0;
      int rows = solrQuery.getRows() != null ? solrQuery.getRows() : 10;
      var pageable = Pageable.ofSize(rows).withPage(rows > 0 ? start / rows : 0);

      return SolrPage.of(beans, pageable, numFound, maxScore);
    } catch (IOException | SolrServerException e) {
      throw new SolrException("Failed to query collection: " + collection, e);
    }
  }

  @Override
  public <T> SolrPage<T> queryForPage(SimpleQuery query, Class<T> type, Pageable pageable) {
    var collection = SolrDocumentResolver.resolveCollection(type, environment);
    query.setPageable(pageable);
    return queryForPage(collection, query, type);
  }

  @Override
  public <T> HighlightPage<T> queryForHighlightPage(String collection, SimpleQuery query, Class<T> type) {
    try {
      var solrQuery = query.toSolrQuery();
      QueryResponse response = solrClient.query(collection, solrQuery);
      var docs = response.getResults();
      var beans = mapDocuments(docs, type);
      long numFound = docs.getNumFound();
      var highlighting = response.getHighlighting();

      int start = solrQuery.getStart() != null ? solrQuery.getStart() : 0;
      int rows = solrQuery.getRows() != null ? solrQuery.getRows() : 10;
      var pageable = Pageable.ofSize(rows).withPage(rows > 0 ? start / rows : 0);

      var entries = new ArrayList<HighlightEntry<T>>();
      for (int i = 0; i < beans.size(); i++) {
        var docId = docs.get(i).getFieldValue("id");
        var docHighlights = highlighting != null && docId != null
            ? highlighting.getOrDefault(docId.toString(), Map.of())
            : Map.<String, List<String>>of();
        entries.add(new HighlightEntry<>(beans.get(i), docHighlights));
      }

      return new HighlightPage<>(beans, pageable, numFound, entries);
    } catch (IOException | SolrServerException e) {
      throw new SolrException("Failed to query with highlights for collection: " + collection, e);
    }
  }

  @Override
  public <T> FacetPage<T> queryForFacetPage(String collection, SimpleQuery query, Class<T> type) {
    try {
      var solrQuery = query.toSolrQuery();
      QueryResponse response = solrClient.query(collection, solrQuery);
      var docs = response.getResults();
      var beans = mapDocuments(docs, type);
      long numFound = docs.getNumFound();

      int start = solrQuery.getStart() != null ? solrQuery.getStart() : 0;
      int rows = solrQuery.getRows() != null ? solrQuery.getRows() : 10;
      var pageable = Pageable.ofSize(rows).withPage(rows > 0 ? start / rows : 0);

      var facetFieldMap = new LinkedHashMap<String, List<FacetFieldEntry>>();
      var facetFieldsResponse = response.getFacetFields();
      if (facetFieldsResponse != null) {
        for (var facetField : facetFieldsResponse) {
          var entries = facetField.getValues().stream()
              .map(count -> new FacetFieldEntry(count.getName(), count.getCount()))
              .toList();
          facetFieldMap.put(facetField.getName(), entries);
        }
      }

      var facetQueryEntries = new ArrayList<FacetQueryEntry>();
      var facetQueryResponse = response.getFacetQuery();
      if (facetQueryResponse != null) {
        facetQueryResponse.forEach((q, c) ->
            facetQueryEntries.add(new FacetQueryEntry(q, c)));
      }

      return new FacetPage<>(beans, pageable, numFound, facetFieldMap, facetQueryEntries);
    } catch (IOException | SolrServerException e) {
      throw new SolrException("Failed to query with facets for collection: " + collection, e);
    }
  }

  @Override
  public long count(String collection, SimpleQuery query) {
    return count(collection, query.toSolrQuery());
  }

  @Override
  public long count(String collection, SolrQuery query) {
    try {
      var countQuery = query.getCopy();
      countQuery.setRows(0);
      QueryResponse response = solrClient.query(collection, countQuery);
      return response.getResults().getNumFound();
    } catch (IOException | SolrServerException e) {
      throw new SolrException("Failed to count in collection: " + collection, e);
    }
  }

  @Override
  public void deleteById(String collection, String id) {
    try {
      solrClient.deleteById(collection, id);
      commitIfImmediate(collection);
    } catch (IOException | SolrServerException e) {
      throw new SolrException(
          "Failed to delete entity by id '%s' from collection: %s".formatted(id, collection), e);
    }
  }

  @Override
  public void deleteByQuery(String collection, String query) {
    try {
      solrClient.deleteByQuery(collection, query);
      commitIfImmediate(collection);
    } catch (IOException | SolrServerException e) {
      throw new SolrException("Failed to delete by query from collection: " + collection, e);
    }
  }

  @Override
  public void commit(String collection) {
    try {
      solrClient.commit(collection);
    } catch (IOException | SolrServerException e) {
      throw new SolrException("Failed to commit collection: " + collection, e);
    }
  }

  @Override
  public void softCommit(String collection) {
    try {
      solrClient.commit(collection, true, true, true);
    } catch (IOException | SolrServerException e) {
      throw new SolrException("Failed to soft commit collection: " + collection, e);
    }
  }

  @Override
  public <T> CursorResult<T> queryWithCursor(String collection, SimpleQuery query, Class<T> type) {
    try {
      var solrQuery = query.toSolrQuery();
      QueryResponse response = solrClient.query(collection, solrQuery);
      var beans = mapDocuments(response.getResults(), type);
      var nextCursorMark = response.getNextCursorMark();
      return CursorResult.of(beans, query.getCursorMark(), nextCursorMark);
    } catch (IOException | SolrServerException e) {
      throw new SolrException("Failed to query with cursor for collection: " + collection, e);
    }
  }

  @Override
  public List<Map<String, Object>> stream(String collection, StreamingExpression expression) {
    try {
      var params = new ModifiableSolrParams();
      params.set("expr", expression.getExpression());
      var request = new GenericSolrRequest(
          SolrRequest.METHOD.POST, "/stream", SolrRequest.SolrRequestType.STREAMING, params)
          .setRequiresCollection(true);
      var response = solrClient.request(request, collection);
      return parseTuples(response);
    } catch (IOException | SolrServerException e) {
      throw new SolrException(
          "Failed to execute streaming expression on collection: " + collection, e);
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> parseTuples(NamedList<Object> response) {
    var resultSetRaw = response.get("result-set");

    // JavaBin wire path: streaming handler encodes result-set as MAP_ENTRY_ITER → Map
    // Unit-test path: mocked responses use NamedList for result-set
    Object docsRaw;
    if (resultSetRaw instanceof NamedList<?> resultSetList) {
      docsRaw = resultSetList.get("docs");
    } else if (resultSetRaw instanceof Map<?, ?> resultSetMap) {
      docsRaw = resultSetMap.get("docs");
    } else {
      return List.of();
    }

    if (!(docsRaw instanceof List<?> docList)) {
      return List.of();
    }

    var tuples = new ArrayList<Map<String, Object>>();
    for (var doc : docList) {
      if (doc instanceof NamedList<?> tupleList) {
        // Unit-test path: mocked responses use NamedList tuples
        if (tupleList.get("EOF") != null) {
          continue;
        }
        var map = new HashMap<String, Object>();
        for (var entry : (NamedList<Object>) tupleList) {
          map.put(entry.getKey(), entry.getValue());
        }
        tuples.add(map);
      } else if (doc instanceof Map<?, ?> tupleMap) {
        // JavaBin wire path: individual tuples decoded as MAP_ENTRY_ITER → LinkedHashMap
        if (tupleMap.containsKey("EOF")) {
          continue;
        }
        var map = new HashMap<String, Object>();
        for (var entry : tupleMap.entrySet()) {
          if (entry.getKey() instanceof String key) {
            map.put(key, entry.getValue());
          }
        }
        tuples.add(map);
      }
    }
    return tuples;
  }

  public SolrClient getSolrClient() {
    return solrClient;
  }

  private <T> List<T> mapDocuments(SolrDocumentList docs, Class<T> type) {
    var reader = new SolrDocumentReader<>(type);
    return docs.stream()
        .map(reader::convert)
        .toList();
  }

  private void commitIfImmediate(String collection) {
    if (commitMode == CommitMode.IMMEDIATE) {
      commit(collection);
    }
  }
}
