package com.tomaytotomato.data.solr;

import com.tomaytotomato.data.solr.mapping.SolrEntity;
import com.tomaytotomato.data.solr.query.Criteria;
import com.tomaytotomato.data.solr.query.HighlightOptions;
import com.tomaytotomato.data.solr.query.SimpleQuery;
import com.tomaytotomato.data.solr.query.StreamingExpression;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolrTemplateTest {

  private static final String COLLECTION = "test-collection";

  @Mock
  private SolrClient solrClient;

  private SolrTemplate template;

  @BeforeEach
  void setUp() {
    template = new SolrTemplate(solrClient);
  }

  static TestDocument document(String id) {
    var doc = new TestDocument();
    doc.id = id;
    return doc;
  }

  static org.apache.solr.common.SolrDocument solrDoc(String id) {
    var doc = new org.apache.solr.common.SolrDocument();
    doc.setField("id", id);
    return doc;
  }

  static SolrDocumentList docList(org.apache.solr.common.SolrDocument... docs) {
    var list = new SolrDocumentList();
    for (var doc : docs) {
      list.add(doc);
    }
    return list;
  }

  public static class TestDocument {
    @Field public String id;
  }

  @SolrEntity(collection = "annotated-collection")
  public static class AnnotatedDocument {
    @Field public String id;
  }

  @Nested
  class Save {

    @Test
    void delegatesToSolrClientAdd() throws Exception {
      var entity = document("1");
      var result = template.save(COLLECTION, entity);
      verify(solrClient).add(eq(COLLECTION), any(SolrInputDocument.class));
      assertThat(result).isSameAs(entity);
    }

    @Test
    void wrapsIOExceptionInSolrException() throws Exception {
      when(solrClient.add(eq(COLLECTION), any(SolrInputDocument.class)))
          .thenThrow(new IOException("network error"));
      assertThatThrownBy(() -> template.save(COLLECTION, document("1")))
          .isInstanceOf(SolrException.class)
          .hasMessageContaining(COLLECTION)
          .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void wrapsSolrServerExceptionInSolrException() throws Exception {
      when(solrClient.add(eq(COLLECTION), any(SolrInputDocument.class)))
          .thenThrow(new SolrServerException("server error"));
      assertThatThrownBy(() -> template.save(COLLECTION, document("1")))
          .isInstanceOf(SolrException.class)
          .hasCauseInstanceOf(SolrServerException.class);
    }
  }

  @Nested
  class SaveAll {

    @Test
    @SuppressWarnings("unchecked")
    void delegatesToSolrClientAddWithDocumentList() throws Exception {
      var entities = List.of(document("1"), document("2"));
      var result = template.saveAll(COLLECTION, entities);
      verify(solrClient).add(eq(COLLECTION), any(List.class));
      assertThat(result).containsExactlyElementsOf(entities);
    }

    @Test
    @SuppressWarnings("unchecked")
    void wrapsIOExceptionInSolrException() throws Exception {
      when(solrClient.add(eq(COLLECTION), any(List.class)))
          .thenThrow(new IOException("network error"));
      assertThatThrownBy(() -> template.saveAll(COLLECTION, List.of(document("1"))))
          .isInstanceOf(SolrException.class)
          .hasMessageContaining(COLLECTION)
          .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void wrapsSolrServerExceptionInSolrException() throws Exception {
      when(solrClient.add(eq(COLLECTION), any(List.class)))
          .thenThrow(new SolrServerException("server error"));
      assertThatThrownBy(() -> template.saveAll(COLLECTION, List.of(document("1"))))
          .isInstanceOf(SolrException.class)
          .hasCauseInstanceOf(SolrServerException.class);
    }
  }

  @Nested
  class SavePartialUpdate {

    @Test
    void delegatesToSolrClientAddWithInputDocument() throws Exception {
      var update = new PartialUpdate("doc-1")
          .set("title", "New Title")
          .increment("views", 1);

      template.savePartialUpdate(COLLECTION, update);

      var captor = org.mockito.ArgumentCaptor.forClass(org.apache.solr.common.SolrInputDocument.class);
      verify(solrClient).add(eq(COLLECTION), captor.capture());
      var inputDoc = captor.getValue();
      assertThat(inputDoc.getFieldValue("id")).isEqualTo("doc-1");
      assertThat(inputDoc.getFieldValue("title")).isEqualTo(Map.of("set", "New Title"));
      assertThat(inputDoc.getFieldValue("views")).isEqualTo(Map.of("inc", 1));
    }

    @Test
    void wrapsIOExceptionInSolrException() throws Exception {
      when(solrClient.add(eq(COLLECTION), any(org.apache.solr.common.SolrInputDocument.class)))
          .thenThrow(new IOException("network error"));

      var update = new PartialUpdate("doc-1").set("title", "New");

      assertThatThrownBy(() -> template.savePartialUpdate(COLLECTION, update))
          .isInstanceOf(SolrException.class)
          .hasMessageContaining(COLLECTION)
          .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void wrapsSolrServerExceptionInSolrException() throws Exception {
      when(solrClient.add(eq(COLLECTION), any(org.apache.solr.common.SolrInputDocument.class)))
          .thenThrow(new SolrServerException("server error"));

      var update = new PartialUpdate("doc-1").set("title", "New");

      assertThatThrownBy(() -> template.savePartialUpdate(COLLECTION, update))
          .isInstanceOf(SolrException.class)
          .hasCauseInstanceOf(SolrServerException.class);
    }
  }

  @Nested
  class FindById {

    @Test
    void returnsOptionalOfEntityWhenFound() throws Exception {
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(docList(solrDoc("42")));
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var result = template.findById(COLLECTION, "42", TestDocument.class);

      assertThat(result).isPresent();
      assertThat(result.get().id).isEqualTo("42");
    }

    @Test
    void returnsOptionalEmptyWhenNotFound() throws Exception {
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(new SolrDocumentList());
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var result = template.findById(COLLECTION, "99", TestDocument.class);

      assertThat(result).isEmpty();
    }

    @Test
    void wrapsIOExceptionInSolrException() throws Exception {
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class)))
          .thenThrow(new IOException("network error"));
      assertThatThrownBy(() -> template.findById(COLLECTION, "1", TestDocument.class))
          .isInstanceOf(SolrException.class)
          .hasMessageContaining("1")
          .hasMessageContaining(COLLECTION)
          .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void wrapsSolrServerExceptionInSolrException() throws Exception {
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class)))
          .thenThrow(new SolrServerException("server error"));
      assertThatThrownBy(() -> template.findById(COLLECTION, "1", TestDocument.class))
          .isInstanceOf(SolrException.class)
          .hasCauseInstanceOf(SolrServerException.class);
    }
  }

  @Nested
  class Query {

    @Test
    void delegatesToSolrClientQueryAndReturnsBeans() throws Exception {
      var solrQuery = new SolrQuery("category:books");
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(docList(solrDoc("10")));
      when(solrClient.query(COLLECTION, solrQuery)).thenReturn(response);

      var result = template.query(COLLECTION, solrQuery, TestDocument.class);

      assertThat(result).hasSize(1);
      assertThat(result.getFirst().id).isEqualTo("10");
    }

    @Test
    void returnsEmptyListWhenNoResultsFound() throws Exception {
      var solrQuery = new SolrQuery("category:nothing");
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(new SolrDocumentList());
      when(solrClient.query(COLLECTION, solrQuery)).thenReturn(response);

      var result = template.query(COLLECTION, solrQuery, TestDocument.class);

      assertThat(result).isEmpty();
    }

    @Test
    void wrapsIOExceptionInSolrException() throws Exception {
      var solrQuery = new SolrQuery("*:*");
      when(solrClient.query(COLLECTION, solrQuery)).thenThrow(new IOException("network error"));
      assertThatThrownBy(() -> template.query(COLLECTION, solrQuery, TestDocument.class))
          .isInstanceOf(SolrException.class)
          .hasMessageContaining(COLLECTION)
          .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void wrapsSolrServerExceptionInSolrException() throws Exception {
      var solrQuery = new SolrQuery("*:*");
      when(solrClient.query(COLLECTION, solrQuery))
          .thenThrow(new SolrServerException("server error"));
      assertThatThrownBy(() -> template.query(COLLECTION, solrQuery, TestDocument.class))
          .isInstanceOf(SolrException.class)
          .hasCauseInstanceOf(SolrServerException.class);
    }
  }

  @Nested
  class DeleteById {

    @Test
    void delegatesToSolrClientDeleteById() throws Exception {
      template.deleteById(COLLECTION, "5");
      verify(solrClient).deleteById(COLLECTION, "5");
    }

    @Test
    void wrapsIOExceptionInSolrException() throws Exception {
      when(solrClient.deleteById(COLLECTION, "5")).thenThrow(new IOException("network error"));
      assertThatThrownBy(() -> template.deleteById(COLLECTION, "5"))
          .isInstanceOf(SolrException.class)
          .hasMessageContaining("5")
          .hasMessageContaining(COLLECTION)
          .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void wrapsSolrServerExceptionInSolrException() throws Exception {
      when(solrClient.deleteById(COLLECTION, "5"))
          .thenThrow(new SolrServerException("server error"));
      assertThatThrownBy(() -> template.deleteById(COLLECTION, "5"))
          .isInstanceOf(SolrException.class)
          .hasCauseInstanceOf(SolrServerException.class);
    }
  }

  @Nested
  class DeleteByQuery {

    @Test
    void delegatesToSolrClientDeleteByQuery() throws Exception {
      template.deleteByQuery(COLLECTION, "status:inactive");
      verify(solrClient).deleteByQuery(COLLECTION, "status:inactive");
    }

    @Test
    void wrapsIOExceptionInSolrException() throws Exception {
      when(solrClient.deleteByQuery(COLLECTION, "status:inactive"))
          .thenThrow(new IOException("network error"));
      assertThatThrownBy(() -> template.deleteByQuery(COLLECTION, "status:inactive"))
          .isInstanceOf(SolrException.class)
          .hasMessageContaining(COLLECTION)
          .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void wrapsSolrServerExceptionInSolrException() throws Exception {
      when(solrClient.deleteByQuery(COLLECTION, "status:inactive"))
          .thenThrow(new SolrServerException("server error"));
      assertThatThrownBy(() -> template.deleteByQuery(COLLECTION, "status:inactive"))
          .isInstanceOf(SolrException.class)
          .hasCauseInstanceOf(SolrServerException.class);
    }
  }

  @Nested
  class Commit {

    @Test
    void delegatesToSolrClientCommit() throws Exception {
      template.commit(COLLECTION);
      verify(solrClient).commit(COLLECTION);
    }

    @Test
    void wrapsIOExceptionInSolrException() throws Exception {
      when(solrClient.commit(COLLECTION)).thenThrow(new IOException("network error"));
      assertThatThrownBy(() -> template.commit(COLLECTION))
          .isInstanceOf(SolrException.class)
          .hasMessageContaining(COLLECTION)
          .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void wrapsSolrServerExceptionInSolrException() throws Exception {
      when(solrClient.commit(COLLECTION)).thenThrow(new SolrServerException("server error"));
      assertThatThrownBy(() -> template.commit(COLLECTION))
          .isInstanceOf(SolrException.class)
          .hasCauseInstanceOf(SolrServerException.class);
    }
  }

  @Nested
  class Count {

    @Test
    void doesNotMutateCallerQueryRowsWhenCounting() throws Exception {
      var docList = new SolrDocumentList();
      docList.setNumFound(5L);
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(docList);
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var originalQuery = new SolrQuery("*:*");
      originalQuery.setRows(10);

      template.count(COLLECTION, originalQuery);

      assertThat(originalQuery.getRows()).isEqualTo(10);
    }

    @Test
    void returnsNumFoundFromResponse() throws Exception {
      var docList = new SolrDocumentList();
      docList.setNumFound(42L);
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(docList);
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var result = template.count(COLLECTION, new SolrQuery("*:*"));

      assertThat(result).isEqualTo(42L);
    }

    @Test
    void countsViaSimpleQueryByDelegatingToSolrQuery() throws Exception {
      var docList = new SolrDocumentList();
      docList.setNumFound(17L);
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(docList);
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var simpleQuery = new SimpleQuery(Criteria.where("status").is("active"));
      var result = template.count(COLLECTION, simpleQuery);

      assertThat(result).isEqualTo(17L);
    }

    @Test
    void wrapsIOExceptionFromSimpleQueryCount() throws Exception {
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class)))
          .thenThrow(new IOException("network error"));

      var simpleQuery = new SimpleQuery(Criteria.where("*").is("*"));

      assertThatThrownBy(() -> template.count(COLLECTION, simpleQuery))
          .isInstanceOf(SolrException.class)
          .hasMessageContaining(COLLECTION)
          .hasCauseInstanceOf(IOException.class);
    }
  }

  @Nested
  class SaveWithAnnotatedCollection {

    @Test
    void resolvesCollectionFromSolrDocumentAnnotation() throws Exception {
      var entity = new AnnotatedDocument();
      entity.id = "1";

      var result = template.save(entity);

      verify(solrClient).add(eq("annotated-collection"), any(SolrInputDocument.class));
      assertThat(result).isSameAs(entity);
    }
  }

  @Nested
  class FindByIdWithAnnotatedCollection {

    @Test
    void resolvesCollectionFromSolrDocumentAnnotation() throws Exception {
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(docList(solrDoc("42")));
      when(solrClient.query(eq("annotated-collection"), any(SolrParams.class))).thenReturn(response);

      var result = template.findById("42", AnnotatedDocument.class);

      assertThat(result).isPresent();
      assertThat(result.get().id).isEqualTo("42");
    }
  }

  @Nested
  class SoftCommit {

    @Test
    void delegatesToSolrClientSoftCommit() throws Exception {
      template.softCommit(COLLECTION);
      verify(solrClient).commit(COLLECTION, true, true, true);
    }

    @Test
    void wrapsIOExceptionInSolrException() throws Exception {
      when(solrClient.commit(COLLECTION, true, true, true)).thenThrow(new IOException("network error"));
      assertThatThrownBy(() -> template.softCommit(COLLECTION))
          .isInstanceOf(SolrException.class)
          .hasMessageContaining(COLLECTION)
          .hasCauseInstanceOf(IOException.class);
    }
  }

  @Nested
  class CommitModeImmediate {

    private SolrTemplate immediateTemplate;

    @BeforeEach
    void setUp() {
      immediateTemplate = new SolrTemplate(solrClient, CommitMode.IMMEDIATE);
    }

    @Test
    void commitsAfterSaveWhenModeIsImmediate() throws Exception {
      immediateTemplate.save(COLLECTION, document("1"));

      verify(solrClient).add(eq(COLLECTION), any(SolrInputDocument.class));
      verify(solrClient).commit(COLLECTION);
    }

    @Test
    void doesNotCommitAfterSaveWhenModeIsNone() throws Exception {
      template.save(COLLECTION, document("1"));

      verify(solrClient).add(eq(COLLECTION), any(SolrInputDocument.class));
      verify(solrClient, never()).commit(COLLECTION);
    }

    @Test
    @SuppressWarnings("unchecked")
    void commitsAfterSaveAllWhenModeIsImmediate() throws Exception {
      immediateTemplate.saveAll(COLLECTION, List.of(document("1"), document("2")));

      verify(solrClient).add(eq(COLLECTION), any(List.class));
      verify(solrClient).commit(COLLECTION);
    }

    @Test
    void commitsAfterPartialUpdateWhenModeIsImmediate() throws Exception {
      var update = new PartialUpdate("doc-1").set("title", "Updated");

      immediateTemplate.savePartialUpdate(COLLECTION, update);

      verify(solrClient).add(eq(COLLECTION), any(org.apache.solr.common.SolrInputDocument.class));
      verify(solrClient).commit(COLLECTION);
    }

    @Test
    void commitsAfterDeleteByIdWhenModeIsImmediate() throws Exception {
      immediateTemplate.deleteById(COLLECTION, "5");

      verify(solrClient).deleteById(COLLECTION, "5");
      verify(solrClient).commit(COLLECTION);
    }

    @Test
    void commitsAfterDeleteByQueryWhenModeIsImmediate() throws Exception {
      immediateTemplate.deleteByQuery(COLLECTION, "status:inactive");

      verify(solrClient).deleteByQuery(COLLECTION, "status:inactive");
      verify(solrClient).commit(COLLECTION);
    }
  }

  @Nested
  class QueryWithCursor {

    @Test
    void returnsContentWithNextCursorMarkAndHasMoreTrueWhenCursorAdvances() throws Exception {
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(docList(solrDoc("1")));
      when(response.getNextCursorMark()).thenReturn("AoE=");
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var query = new SimpleQuery(Criteria.where("*").is("*"))
          .setCursorMark("*");

      var result = template.queryWithCursor(COLLECTION, query, TestDocument.class);

      assertThat(result.content()).hasSize(1);
      assertThat(result.content().getFirst().id).isEqualTo("1");
      assertThat(result.cursorMark()).isEqualTo("AoE=");
      assertThat(result.hasMore()).isTrue();
    }

    @Test
    void hasMoreIsFalseWhenNextCursorMarkEqualsRequestCursorMark() throws Exception {
      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(docList(solrDoc("2")));
      when(response.getNextCursorMark()).thenReturn("AoE=");
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var query = new SimpleQuery(Criteria.where("*").is("*"))
          .setCursorMark("AoE=");

      var result = template.queryWithCursor(COLLECTION, query, TestDocument.class);

      assertThat(result.hasMore()).isFalse();
      assertThat(result.cursorMark()).isEqualTo("AoE=");
    }

    @Test
    void wrapsIOExceptionInSolrException() throws Exception {
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class)))
          .thenThrow(new IOException("network error"));

      var query = new SimpleQuery(Criteria.where("*").is("*"))
          .setCursorMark("*");

      assertThatThrownBy(() -> template.queryWithCursor(COLLECTION, query, TestDocument.class))
          .isInstanceOf(SolrException.class)
          .hasMessageContaining(COLLECTION)
          .hasCauseInstanceOf(IOException.class);
    }
  }

  @Nested
  class QueryForPageWithAnnotatedCollection {

    @Test
    void resolvesCollectionFromSolrDocumentAnnotation() throws Exception {
      var results = docList(solrDoc("1"));
      results.setNumFound(1L);
      results.setMaxScore(1.5f);

      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(results);
      when(solrClient.query(eq("annotated-collection"), any(SolrParams.class))).thenReturn(response);

      var query = new SimpleQuery(Criteria.where("*").is("*"));
      var result = template.queryForPage(query, AnnotatedDocument.class, Pageable.ofSize(10));

      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().getFirst().id).isEqualTo("1");
      verify(solrClient).query(eq("annotated-collection"), any(SolrParams.class));
    }
  }

  @Nested
  class QueryForHighlightPage {

    @Test
    void returnsHighlightEntriesPairedWithEntities() throws Exception {
      var results = docList(solrDoc("1"));
      results.setNumFound(1L);

      var highlights = Map.of("1", Map.of("title", List.of("a <em>match</em>")));

      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(results);
      when(response.getHighlighting()).thenReturn(highlights);
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var options = new HighlightOptions().addField("title");
      var query = new SimpleQuery(Criteria.where("*").is("*")).setHighlightOptions(options);

      var result = template.queryForHighlightPage(COLLECTION, query, TestDocument.class);

      assertThat(result.getHighlighted()).hasSize(1);
      assertThat(result.getHighlighted().get(0).entity().id).isEqualTo("1");
      assertThat(result.getHighlighted().get(0).highlights())
          .containsKey("title")
          .extractingByKey("title")
          .asList()
          .containsExactly("a <em>match</em>");
    }

    @Test
    void returnsEmptyHighlightsWhenNoHighlightingInResponse() throws Exception {
      var results = docList(solrDoc("2"));
      results.setNumFound(1L);

      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(results);
      when(response.getHighlighting()).thenReturn(null);
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var query = new SimpleQuery(Criteria.where("*").is("*"));
      var result = template.queryForHighlightPage(COLLECTION, query, TestDocument.class);

      assertThat(result.getHighlighted()).hasSize(1);
      assertThat(result.getHighlighted().get(0).highlights()).isEmpty();
    }

    @Test
    void returnsEmptyHighlightsWhenDocumentIdIsNull() throws Exception {
      var solrDoc = new org.apache.solr.common.SolrDocument();
      var results = docList(solrDoc);
      results.setNumFound(1L);

      var highlights = Map.of("3", Map.of("title", List.of("match")));

      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(results);
      when(response.getHighlighting()).thenReturn(highlights);
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var query = new SimpleQuery(Criteria.where("*").is("*"));
      var result = template.queryForHighlightPage(COLLECTION, query, TestDocument.class);

      assertThat(result.getHighlighted()).hasSize(1);
      assertThat(result.getHighlighted().get(0).highlights()).isEmpty();
    }

    @Test
    void wrapsIOExceptionInSolrException() throws Exception {
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class)))
          .thenThrow(new IOException("network error"));

      var query = new SimpleQuery(Criteria.where("*").is("*"));

      assertThatThrownBy(() -> template.queryForHighlightPage(COLLECTION, query, TestDocument.class))
          .isInstanceOf(SolrException.class)
          .hasMessageContaining(COLLECTION)
          .hasCauseInstanceOf(IOException.class);
    }
  }

  @Nested
  class Stream {

    @Test
    void returnsListOfTuplesFromResultSet() throws Exception {
      var tuple1 = new NamedList<Object>();
      tuple1.add("id", "1");
      tuple1.add("title", "Make It So");

      var tuple2 = new NamedList<Object>();
      tuple2.add("id", "2");
      tuple2.add("title", "The Final Frontier");

      var eofTuple = new NamedList<Object>();
      eofTuple.add("EOF", Boolean.TRUE);

      var docs = new ArrayList<>();
      docs.add(tuple1);
      docs.add(tuple2);
      docs.add(eofTuple);

      var resultSet = new NamedList<Object>();
      resultSet.add("docs", docs);

      var responseBody = new NamedList<Object>();
      responseBody.add("result-set", resultSet);

      when(solrClient.request(any(), eq(COLLECTION))).thenReturn(responseBody);

      var expression = StreamingExpression.search("books").query("*:*").build();
      var result = template.stream(COLLECTION, expression);

      assertThat(result).hasSize(2);
      assertThat(result.get(0)).containsEntry("id", "1").containsEntry("title", "Make It So");
      assertThat(result.get(1)).containsEntry("id", "2").containsEntry("title", "The Final Frontier");
    }

    @Test
    void returnsEmptyListWhenNoTuplesInResultSet() throws Exception {
      var eofTuple = new NamedList<Object>();
      eofTuple.add("EOF", Boolean.TRUE);

      var docs = new ArrayList<>();
      docs.add(eofTuple);

      var resultSet = new NamedList<Object>();
      resultSet.add("docs", docs);

      var responseBody = new NamedList<Object>();
      responseBody.add("result-set", resultSet);

      when(solrClient.request(any(), eq(COLLECTION))).thenReturn(responseBody);

      var expression = StreamingExpression.search("books").query("status:none").build();
      var result = template.stream(COLLECTION, expression);

      assertThat(result).isEmpty();
    }

    @Test
    void wrapsIOExceptionInSolrException() throws Exception {
      when(solrClient.request(any(), eq(COLLECTION))).thenThrow(new IOException("network error"));

      var expression = StreamingExpression.of("search(books, q=\"*:*\")");

      assertThatThrownBy(() -> template.stream(COLLECTION, expression))
          .isInstanceOf(SolrException.class)
          .hasMessageContaining(COLLECTION)
          .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void wrapsSolrServerExceptionInSolrException() throws Exception {
      when(solrClient.request(any(), eq(COLLECTION))).thenThrow(new SolrServerException("server error"));

      var expression = StreamingExpression.of("search(books, q=\"*:*\")");

      assertThatThrownBy(() -> template.stream(COLLECTION, expression))
          .isInstanceOf(SolrException.class)
          .hasCauseInstanceOf(SolrServerException.class);
    }

    @Test
    void returnsEmptyListWhenResultSetIsAbsentFromResponse() throws Exception {
      var responseBody = new NamedList<Object>();
      when(solrClient.request(any(), eq(COLLECTION))).thenReturn(responseBody);

      var expression = StreamingExpression.of("search(books, q=\"*:*\")");
      var result = template.stream(COLLECTION, expression);

      assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyListWhenDocsIsNotAList() throws Exception {
      var resultSet = new NamedList<Object>();
      resultSet.add("docs", "not-a-list");

      var responseBody = new NamedList<Object>();
      responseBody.add("result-set", resultSet);

      when(solrClient.request(any(), eq(COLLECTION))).thenReturn(responseBody);

      var expression = StreamingExpression.of("search(books, q=\"*:*\")");
      var result = template.stream(COLLECTION, expression);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class QueryForPageWithPageable {

    @Test
    void appliesPageablePageSizeAndOffsetToSolrQuery() throws Exception {
      var results = docList(solrDoc("1"), solrDoc("2"));
      results.setNumFound(50L);

      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(results);
      when(solrClient.query(eq("annotated-collection"), any(SolrParams.class))).thenReturn(response);

      var query = new SimpleQuery(Criteria.where("*").is("*"));
      var pageable = Pageable.ofSize(5).withPage(2); // offset = 10, rows = 5

      template.queryForPage(query, AnnotatedDocument.class, pageable);

      var captor = org.mockito.ArgumentCaptor.forClass(SolrParams.class);
      verify(solrClient).query(eq("annotated-collection"), captor.capture());
      var capturedParams = captor.getValue();
      assertThat(capturedParams.getInt("start")).isEqualTo(10);
      assertThat(capturedParams.getInt("rows")).isEqualTo(5);
    }
  }

  @Nested
  class QueryForFacetPage {

    @Test
    void returnsFieldFacetsParsedFromResponse() throws Exception {
      var results = docList(solrDoc("1"));
      results.setNumFound(1L);

      var genreField = new org.apache.solr.client.solrj.response.FacetField("genre");
      genreField.add("sci-fi", 42L);
      genreField.add("drama", 17L);

      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(results);
      when(response.getFacetFields()).thenReturn(List.of(genreField));
      when(response.getFacetQuery()).thenReturn(Map.of());
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var query = new SimpleQuery(Criteria.where("*").is("*"));
      var result = template.queryForFacetPage(COLLECTION, query, TestDocument.class);

      assertThat(result.getFacetField("genre")).hasSize(2);
      assertThat(result.getFacetField("genre").get(0).value()).isEqualTo("sci-fi");
      assertThat(result.getFacetField("genre").get(0).count()).isEqualTo(42L);
      assertThat(result.getFacetField("genre").get(1).value()).isEqualTo("drama");
      assertThat(result.getFacetField("genre").get(1).count()).isEqualTo(17L);
    }

    @Test
    void returnsQueryFacetsParsedFromResponse() throws Exception {
      var results = new SolrDocumentList();
      results.setNumFound(0L);

      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(results);
      when(response.getFacetFields()).thenReturn(List.of());
      when(response.getFacetQuery()).thenReturn(Map.of("price:[0 TO 10]", 23, "price:[10 TO 100]", 87));
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var query = new SimpleQuery(Criteria.where("*").is("*"));
      var result = template.queryForFacetPage(COLLECTION, query, TestDocument.class);

      assertThat(result.getFacetQueries()).hasSize(2);
      assertThat(result.getFacetQueries())
          .extracting(com.tomaytotomato.data.solr.query.FacetQueryEntry::query)
          .containsExactlyInAnyOrder("price:[0 TO 10]", "price:[10 TO 100]");
    }

    @Test
    void returnsEmptyFacetsWhenNullFacetFields() throws Exception {
      var results = new SolrDocumentList();
      results.setNumFound(0L);

      var response = mock(QueryResponse.class);
      when(response.getResults()).thenReturn(results);
      when(response.getFacetFields()).thenReturn(null);
      when(response.getFacetQuery()).thenReturn(null);
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class))).thenReturn(response);

      var query = new SimpleQuery(Criteria.where("*").is("*"));
      var result = template.queryForFacetPage(COLLECTION, query, TestDocument.class);

      assertThat(result.getFacetFields()).isEmpty();
      assertThat(result.getFacetQueries()).isEmpty();
    }

    @Test
    void wrapsIOExceptionInSolrException() throws Exception {
      when(solrClient.query(eq(COLLECTION), any(SolrParams.class)))
          .thenThrow(new IOException("network error"));

      var query = new SimpleQuery(Criteria.where("*").is("*"));

      assertThatThrownBy(() -> template.queryForFacetPage(COLLECTION, query, TestDocument.class))
          .isInstanceOf(SolrException.class)
          .hasMessageContaining(COLLECTION)
          .hasCauseInstanceOf(IOException.class);
    }
  }
}
