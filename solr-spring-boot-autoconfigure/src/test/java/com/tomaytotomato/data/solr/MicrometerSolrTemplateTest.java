package com.tomaytotomato.data.solr;

import com.tomaytotomato.data.solr.query.Criteria;
import com.tomaytotomato.data.solr.query.SimpleQuery;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MicrometerSolrTemplateTest {

  private static final String COLLECTION = "books";

  @Mock
  private SolrClient solrClient;

  private TestObservationRegistry observationRegistry;
  private MicrometerSolrTemplate template;

  @BeforeEach
  void setUp() {
    observationRegistry = TestObservationRegistry.create();
    template = new MicrometerSolrTemplate(solrClient, CommitMode.NONE, null, observationRegistry);
  }

  static TestDoc doc(String id) {
    var d = new TestDoc();
    d.id = id;
    return d;
  }

  public static class TestDoc {
    @org.apache.solr.client.solrj.beans.Field public String id;
  }

  private TestObservationRegistryAssert thenObservation() {
    return TestObservationRegistryAssert.assertThat(observationRegistry);
  }

  @Nested
  class QueryObservation {

    @Test
    void createsObservationAfterQueryOperation() throws Exception {
      var solrQuery = new SolrQuery("*:*");
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(new SolrDocumentList());
      when(solrClient.query(COLLECTION, solrQuery)).thenReturn(response);

      template.query(COLLECTION, solrQuery, TestDoc.class);

      thenObservation()
          .hasObservationWithNameEqualTo("solr.operation")
          .that()
          .hasLowCardinalityKeyValue("operation", "query")
          .hasLowCardinalityKeyValue("collection", COLLECTION);
    }

    @Test
    void queryResultPassesThroughObservationWrapper() throws Exception {
      var solrQuery = new SolrQuery("title:foo");
      var solrDoc = new SolrDocument();
      solrDoc.setField("id", "1");
      var results = new SolrDocumentList();
      results.add(solrDoc);
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(results);
      when(solrClient.query(COLLECTION, solrQuery)).thenReturn(response);

      var result = template.query(COLLECTION, solrQuery, TestDoc.class);

      assertThat(result).hasSize(1);
      assertThat(result.getFirst().id).isEqualTo("1");
    }

    @Test
    void queryExceptionPropagatesThroughObservationWrapper() throws Exception {
      var solrQuery = new SolrQuery("*:*");
      when(solrClient.query(COLLECTION, solrQuery)).thenThrow(new IOException("network"));

      assertThatThrownBy(() -> template.query(COLLECTION, solrQuery, TestDoc.class))
          .isInstanceOf(SolrException.class)
          .hasCauseInstanceOf(IOException.class);

      thenObservation().hasObservationWithNameEqualTo("solr.operation");
    }
  }

  @Nested
  class QueryForPageObservation {

    @Test
    void createsObservationAfterQueryForPageOperation() throws Exception {
      var results = new SolrDocumentList();
      results.setNumFound(0L);
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(results);
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      template.queryForPage(COLLECTION, new SimpleQuery(Criteria.where("*").is("*")), TestDoc.class);

      thenObservation()
          .hasObservationWithNameEqualTo("solr.operation")
          .that()
          .hasLowCardinalityKeyValue("operation", "queryForPage")
          .hasLowCardinalityKeyValue("collection", COLLECTION);
    }

    @Test
    void queryForPageResultPassesThroughObservationWrapper() throws Exception {
      var solrDoc = new SolrDocument();
      solrDoc.setField("id", "2");
      var results = new SolrDocumentList();
      results.add(solrDoc);
      results.setNumFound(1L);
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(results);
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var result = template.queryForPage(COLLECTION, new SimpleQuery(Criteria.where("*").is("*")), TestDoc.class);

      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().getFirst().id).isEqualTo("2");
    }
  }

  @Nested
  class CountObservation {

    @Test
    void createsObservationAfterCountWithSolrQuery() throws Exception {
      var docList = new SolrDocumentList();
      docList.setNumFound(7L);
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(docList);
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      template.count(COLLECTION, new SolrQuery("*:*"));

      thenObservation()
          .hasObservationWithNameEqualTo("solr.operation")
          .that()
          .hasLowCardinalityKeyValue("operation", "count")
          .hasLowCardinalityKeyValue("collection", COLLECTION);
    }

    @Test
    void createsObservationAfterCountWithSimpleQuery() throws Exception {
      var docList = new SolrDocumentList();
      docList.setNumFound(3L);
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(docList);
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      template.count(COLLECTION, new SimpleQuery(Criteria.where("*").is("*")));

      thenObservation()
          .hasObservationWithNameEqualTo("solr.operation")
          .that()
          .hasLowCardinalityKeyValue("operation", "count");
    }

    @Test
    void countResultPassesThroughObservationWrapper() throws Exception {
      var docList = new SolrDocumentList();
      docList.setNumFound(99L);
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(docList);
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var result = template.count(COLLECTION, new SolrQuery("*:*"));

      assertThat(result).isEqualTo(99L);
    }
  }

  @Nested
  class SaveObservation {

    @Test
    void createsObservationAfterSaveOperation() throws Exception {
      var entity = doc("1");
      template.save(COLLECTION, entity);

      thenObservation()
          .hasObservationWithNameEqualTo("solr.operation")
          .that()
          .hasLowCardinalityKeyValue("operation", "save")
          .hasLowCardinalityKeyValue("collection", COLLECTION);
    }

    @Test
    void saveResultPassesThroughObservationWrapper() throws Exception {
      var entity = doc("42");
      var result = template.save(COLLECTION, entity);

      assertThat(result).isSameAs(entity);
    }

    @Test
    void saveExceptionPropagatesThroughObservationWrapper() throws Exception {
      when(solrClient.add(eq(COLLECTION), any(org.apache.solr.common.SolrInputDocument.class)))
          .thenThrow(new SolrServerException("boom"));

      assertThatThrownBy(() -> template.save(COLLECTION, doc("1")))
          .isInstanceOf(SolrException.class)
          .hasCauseInstanceOf(SolrServerException.class);

      thenObservation().hasObservationWithNameEqualTo("solr.operation");
    }
  }

  @Nested
  class SaveAllObservation {

    @Test
    void createsObservationAfterSaveAllOperation() throws Exception {
      var entities = List.of(doc("1"), doc("2"));
      template.saveAll(COLLECTION, entities);

      thenObservation()
          .hasObservationWithNameEqualTo("solr.operation")
          .that()
          .hasLowCardinalityKeyValue("operation", "saveAll")
          .hasLowCardinalityKeyValue("collection", COLLECTION);
    }

    @Test
    void saveAllResultPassesThroughObservationWrapper() throws Exception {
      var entities = List.of(doc("a"), doc("b"));
      var result = template.saveAll(COLLECTION, entities);

      assertThat(result).containsExactlyElementsOf(entities);
    }
  }

  @Nested
  class DeleteByIdObservation {

    @Test
    void createsObservationAfterDeleteByIdOperation() throws Exception {
      template.deleteById(COLLECTION, "5");

      thenObservation()
          .hasObservationWithNameEqualTo("solr.operation")
          .that()
          .hasLowCardinalityKeyValue("operation", "deleteById")
          .hasLowCardinalityKeyValue("collection", COLLECTION);
    }

    @Test
    void deleteByIdExceptionPropagatesThroughObservationWrapper() throws Exception {
      when(solrClient.deleteById(COLLECTION, "5")).thenThrow(new IOException("net error"));

      assertThatThrownBy(() -> template.deleteById(COLLECTION, "5"))
          .isInstanceOf(SolrException.class)
          .hasCauseInstanceOf(IOException.class);

      thenObservation().hasObservationWithNameEqualTo("solr.operation");
    }
  }

  @Nested
  class DeleteByQueryObservation {

    @Test
    void createsObservationAfterDeleteByQueryOperation() throws Exception {
      template.deleteByQuery(COLLECTION, "status:inactive");

      thenObservation()
          .hasObservationWithNameEqualTo("solr.operation")
          .that()
          .hasLowCardinalityKeyValue("operation", "deleteByQuery")
          .hasLowCardinalityKeyValue("collection", COLLECTION);
    }
  }

  @Nested
  class QueryForHighlightPageObservation {

    @Test
    void createsObservationAfterQueryForHighlightPageOperation() throws Exception {
      var solrDoc = new SolrDocument();
      solrDoc.setField("id", "1");
      var results = new SolrDocumentList();
      results.add(solrDoc);
      results.setNumFound(1L);
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(results);
      when(response.getHighlighting()).thenReturn(Map.of());
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      template.queryForHighlightPage(COLLECTION, new SimpleQuery(Criteria.where("*").is("*")), TestDoc.class);

      thenObservation()
          .hasObservationWithNameEqualTo("solr.operation")
          .that()
          .hasLowCardinalityKeyValue("operation", "queryForHighlightPage")
          .hasLowCardinalityKeyValue("collection", COLLECTION);
    }
  }

  @Nested
  class QueryForFacetPageObservation {

    @Test
    void createsObservationAfterQueryForFacetPageOperation() throws Exception {
      var results = new SolrDocumentList();
      results.setNumFound(0L);
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(results);
      when(response.getFacetFields()).thenReturn(List.of());
      when(response.getFacetQuery()).thenReturn(Map.of());
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      template.queryForFacetPage(COLLECTION, new SimpleQuery(Criteria.where("*").is("*")), TestDoc.class);

      thenObservation()
          .hasObservationWithNameEqualTo("solr.operation")
          .that()
          .hasLowCardinalityKeyValue("operation", "queryForFacetPage")
          .hasLowCardinalityKeyValue("collection", COLLECTION);
    }
  }

  @Nested
  class SavePartialUpdateObservation {

    @Test
    void createsObservationAfterSavePartialUpdateOperation() throws Exception {
      var update = new PartialUpdate("1").set("title", "New Title");
      template.savePartialUpdate(COLLECTION, update);

      thenObservation()
          .hasObservationWithNameEqualTo("solr.operation")
          .that()
          .hasLowCardinalityKeyValue("operation", "partial-update")
          .hasLowCardinalityKeyValue("collection", COLLECTION);
    }

    @Test
    void savePartialUpdateExceptionPropagatesThroughObservationWrapper() throws Exception {
      when(solrClient.add(eq(COLLECTION), any(org.apache.solr.common.SolrInputDocument.class)))
          .thenThrow(new IOException("disk full"));
      var update = new PartialUpdate("1").set("title", "Fail");

      assertThatThrownBy(() -> template.savePartialUpdate(COLLECTION, update))
          .isInstanceOf(SolrException.class)
          .hasCauseInstanceOf(IOException.class);

      thenObservation().hasObservationWithNameEqualTo("solr.operation");
    }
  }

  @Nested
  class CommitObservation {

    @Test
    void createsObservationAfterCommitOperation() throws Exception {
      template.commit(COLLECTION);

      thenObservation()
          .hasObservationWithNameEqualTo("solr.operation")
          .that()
          .hasLowCardinalityKeyValue("operation", "commit")
          .hasLowCardinalityKeyValue("collection", COLLECTION);
    }

    @Test
    void commitExceptionPropagatesThroughObservationWrapper() throws Exception {
      when(solrClient.commit(COLLECTION)).thenThrow(new IOException("network"));

      assertThatThrownBy(() -> template.commit(COLLECTION))
          .isInstanceOf(SolrException.class)
          .hasCauseInstanceOf(IOException.class);

      thenObservation().hasObservationWithNameEqualTo("solr.operation");
    }
  }

  @Nested
  class SoftCommitObservation {

    @Test
    void createsObservationAfterSoftCommitOperation() throws Exception {
      template.softCommit(COLLECTION);

      thenObservation()
          .hasObservationWithNameEqualTo("solr.operation")
          .that()
          .hasLowCardinalityKeyValue("operation", "soft-commit")
          .hasLowCardinalityKeyValue("collection", COLLECTION);
    }

    @Test
    void softCommitExceptionPropagatesThroughObservationWrapper() throws Exception {
      when(solrClient.commit(COLLECTION, true, true, true)).thenThrow(new IOException("timeout"));

      assertThatThrownBy(() -> template.softCommit(COLLECTION))
          .isInstanceOf(SolrException.class)
          .hasCauseInstanceOf(IOException.class);

      thenObservation().hasObservationWithNameEqualTo("solr.operation");
    }
  }

  @Nested
  class QueryWithCursorObservation {

    @Test
    void createsObservationAfterQueryWithCursorOperation() throws Exception {
      var results = new SolrDocumentList();
      results.setNumFound(0L);
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(results);
      when(response.getNextCursorMark()).thenReturn("AoE=");
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var query = new SimpleQuery(Criteria.matchAll());
      query.setCursorMark("*");
      template.queryWithCursor(COLLECTION, query, TestDoc.class);

      thenObservation()
          .hasObservationWithNameEqualTo("solr.operation")
          .that()
          .hasLowCardinalityKeyValue("operation", "cursor-query")
          .hasLowCardinalityKeyValue("collection", COLLECTION);
    }

    @Test
    void queryWithCursorResultPassesThroughObservationWrapper() throws Exception {
      var solrDoc = new SolrDocument();
      solrDoc.setField("id", "42");
      var results = new SolrDocumentList();
      results.add(solrDoc);
      results.setNumFound(1L);
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(results);
      when(response.getNextCursorMark()).thenReturn("AoE=");
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var query = new SimpleQuery(Criteria.matchAll());
      query.setCursorMark("*");
      var result = template.queryWithCursor(COLLECTION, query, TestDoc.class);

      assertThat(result.content()).hasSize(1);
      assertThat(result.content().getFirst().id).isEqualTo("42");
    }
  }

  @Nested
  class ObservationTagsVerification {

    @Test
    void observationHasCorrectOperationAndCollectionTags() throws Exception {
      var solrQuery = new SolrQuery("*:*");
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(new SolrDocumentList());
      when(solrClient.query(COLLECTION, solrQuery)).thenReturn(response);

      template.query(COLLECTION, solrQuery, TestDoc.class);

      thenObservation()
          .hasObservationWithNameEqualTo("solr.operation")
          .that()
          .hasLowCardinalityKeyValue("operation", "query")
          .hasLowCardinalityKeyValue("collection", COLLECTION);
    }

    @Test
    void differentCollectionsProduceSeparateObservations() throws Exception {
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(new SolrDocumentList());
      when(solrClient.query(any(String.class), any(SolrParams.class))).thenReturn(response);

      template.query("books", new SolrQuery("*:*"), TestDoc.class);
      template.query("authors", new SolrQuery("*:*"), TestDoc.class);

      thenObservation()
          .hasNumberOfObservationsEqualTo(2)
          .hasAnObservationWithAKeyValue("collection", "books")
          .hasAnObservationWithAKeyValue("collection", "authors");
    }
  }
}
