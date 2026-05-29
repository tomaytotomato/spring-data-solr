package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.SolrTemplate;
import com.tomaytotomato.data.solr.mapping.SolrDocument;
import com.tomaytotomato.data.solr.query.Criteria;
import com.tomaytotomato.data.solr.query.SimpleQuery;
import java.util.List;
import java.util.Optional;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimpleSolrRepositoryTest {

  @Mock
  private SolrTemplate solrTemplate;

  private SimpleSolrRepository<TestBook, String> repository;

  @BeforeEach
  void setUp() {
    repository = new SimpleSolrRepository<>(solrTemplate, TestBook.class);
  }

  static TestBook book(String id) {
    var book = new TestBook();
    book.id = id;
    return book;
  }

  @SolrDocument(collection = "test")
  static class TestBook {
    @Field
    String id;
  }

  @Nested
  class Save {

    @Test
    void delegatesToTemplateWithResolvedCollection() {
      var entity = book("1");
      when(solrTemplate.save("test", entity)).thenReturn(entity);

      var result = repository.save(entity);

      verify(solrTemplate).save("test", entity);
      assertThat(result).isSameAs(entity);
    }
  }

  @Nested
  class FindById {

    @Test
    void returnsPresentWhenEntityFound() {
      var entity = book("42");
      when(solrTemplate.findById("test", "42", TestBook.class)).thenReturn(Optional.of(entity));

      var result = repository.findById("42");

      assertThat(result).isPresent().contains(entity);
    }

    @Test
    void returnsEmptyWhenEntityNotFound() {
      when(solrTemplate.findById("test", "99", TestBook.class)).thenReturn(Optional.empty());

      var result = repository.findById("99");

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class ExistsById {

    @Test
    void returnsTrueWhenEntityFound() {
      when(solrTemplate.findById("test", "1", TestBook.class)).thenReturn(Optional.of(book("1")));

      assertThat(repository.existsById("1")).isTrue();
    }

    @Test
    void returnsFalseWhenEntityNotFound() {
      when(solrTemplate.findById("test", "1", TestBook.class)).thenReturn(Optional.empty());

      assertThat(repository.existsById("1")).isFalse();
    }
  }

  @Nested
  class FindAllById {

    @Test
    void returnsEmptyListWhenNoIdsProvided() {
      var result = repository.findAllById(List.of());
      assertThat(result).isEmpty();
    }

    @Test
    void buildsIdQueryForNormalIds() {
      when(solrTemplate.query(eq("test"), any(SolrQuery.class), eq(TestBook.class)))
          .thenReturn(List.of());

      repository.findAllById(List.of("1", "2"));

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("test"), captor.capture(), eq(TestBook.class));
      assertThat(captor.getValue().getQuery()).isEqualTo("id:(1 OR 2)");
    }

    @Test
    void escapesSpecialCharactersInIds() {
      when(solrTemplate.query(eq("test"), any(SolrQuery.class), eq(TestBook.class)))
          .thenReturn(List.of());

      repository.findAllById(List.of("book:001", "book(2)"));

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("test"), captor.capture(), eq(TestBook.class));
      assertThat(captor.getValue().getQuery()).isEqualTo("id:(book\\:001 OR book\\(2\\))");
    }

    @Test
    void escapesColonInSingleId() {
      when(solrTemplate.query(eq("test"), any(SolrQuery.class), eq(TestBook.class)))
          .thenReturn(List.of());

      repository.findAllById(List.of("ns:123"));

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("test"), captor.capture(), eq(TestBook.class));
      assertThat(captor.getValue().getQuery()).isEqualTo("id:(ns\\:123)");
    }
  }

  @Nested
  class FindAll {

    @Test
    void queriesWithWildcardQuery() {
      var books = List.of(book("1"), book("2"));
      when(solrTemplate.query(eq("test"), any(), eq(TestBook.class))).thenReturn(books);

      var result = repository.findAll();

      assertThat(result).containsExactlyElementsOf(books);
    }

    @Test
    void setsDefaultRowCapToStayWithinMaxResultWindow() {
      when(solrTemplate.query(eq("test"), any(SolrQuery.class), eq(TestBook.class)))
          .thenReturn(List.of());

      repository.findAll();

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("test"), captor.capture(), eq(TestBook.class));
      assertThat(captor.getValue().getRows()).isEqualTo(SimpleSolrRepository.DEFAULT_MAX_ROWS);
    }

    @Test
    void findAllWithSortSetsDefaultRowCapToStayWithinMaxResultWindow() {
      when(solrTemplate.query(eq("test"), any(SolrQuery.class), eq(TestBook.class)))
          .thenReturn(List.of());

      repository.findAll(Sort.by("id"));

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("test"), captor.capture(), eq(TestBook.class));
      assertThat(captor.getValue().getRows()).isEqualTo(SimpleSolrRepository.DEFAULT_MAX_ROWS);
    }

    @Test
    void queriesForPageWhenPageableProvided() {
      var pageable = PageRequest.of(0, 10);
      when(solrTemplate.queryForPage(eq("test"), any(SimpleQuery.class), eq(TestBook.class)))
          .thenReturn(null);

      repository.findAll(pageable);

      verify(solrTemplate).queryForPage(eq("test"), any(SimpleQuery.class), eq(TestBook.class));
    }
  }

  @Nested
  class Count {

    @Test
    void delegatesToTemplateCount() {
      when(solrTemplate.count(eq("test"), any(SimpleQuery.class))).thenReturn(5L);

      var result = repository.count();

      assertThat(result).isEqualTo(5L);
    }
  }

  @Nested
  class DeleteById {

    @Test
    void delegatesToTemplateDeleteById() {
      repository.deleteById("3");

      verify(solrTemplate).deleteById("test", "3");
    }
  }

  @Nested
  class DeleteAll {

    @Test
    void deletesWithWildcardQueryAndCommits() {
      repository.deleteAll();

      verify(solrTemplate).deleteByQuery("test", "*:*");
      verify(solrTemplate).commit("test");
    }
  }

  @Nested
  class DeleteEntity {

    @Test
    void deletesEntityByExtractingItsId() {
      var entity = book("7");

      repository.delete(entity);

      verify(solrTemplate).deleteById("test", "7");
    }
  }

  @Nested
  class DeleteAllEntities {

    @Test
    void deletesEachEntityInIterable() {
      var entities = List.of(book("10"), book("11"), book("12"));

      repository.deleteAll(entities);

      verify(solrTemplate).deleteById("test", "10");
      verify(solrTemplate).deleteById("test", "11");
      verify(solrTemplate).deleteById("test", "12");
    }
  }
}
