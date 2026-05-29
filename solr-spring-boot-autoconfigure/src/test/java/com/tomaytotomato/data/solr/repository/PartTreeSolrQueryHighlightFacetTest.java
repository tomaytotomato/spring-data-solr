package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.FacetPage;
import com.tomaytotomato.data.solr.HighlightPage;
import com.tomaytotomato.data.solr.SolrTemplate;
import com.tomaytotomato.data.solr.mapping.SolrDocument;
import com.tomaytotomato.data.solr.query.SimpleQuery;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.query.QueryMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class PartTreeSolrQueryHighlightFacetTest {

  @Mock
  private SolrTemplate solrTemplate;

  @SolrDocument(collection = "books")
  public static class Book {
    String id;
    String title;
    String author;
  }

  interface BookRepository extends SolrRepository<Book> {

    @Highlight(fields = "title", prefix = "<mark>", postfix = "</mark>", snippets = 2, fragsize = 150)
    HighlightPage<Book> findByTitleContaining(String term, Pageable pageable);

    @Highlight
    HighlightPage<Book> findByAuthor(String author, Pageable pageable);

    @Facet(fields = {"author"}, queries = {"year:[2000 TO *]"}, minCount = 2, limit = 20)
    FacetPage<Book> findByTitle(String term, Pageable pageable);

    @Facet
    FacetPage<Book> findByAuthorIs(String author, Pageable pageable);
  }

  private PartTreeSolrQuery createQuery(String methodName, Class<?>... paramTypes) throws Exception {
    Method method = BookRepository.class.getMethod(methodName, paramTypes);
    var metadata = new DefaultRepositoryMetadata(BookRepository.class);
    var factory = new SpelAwareProxyProjectionFactory();
    var queryMethod = new QueryMethod(method, metadata, factory);
    return new PartTreeSolrQuery(queryMethod, solrTemplate, method);
  }

  @Nested
  class HighlightDispatch {

    @Test
    void delegatesToQueryForHighlightPageWhenReturnTypeIsHighlightPage() throws Exception {
      var pageable = PageRequest.of(0, 10);
      HighlightPage emptyHighlightPage = new HighlightPage<>(List.of(), pageable, 0L, List.of());
      when(solrTemplate.queryForHighlightPage(eq("books"), any(SimpleQuery.class), eq(Book.class)))
          .thenReturn(emptyHighlightPage);

      var query = createQuery("findByTitleContaining", String.class, Pageable.class);
      var result = query.execute(new Object[]{"spring", pageable});

      assertThat(result).isInstanceOf(HighlightPage.class);
      verify(solrTemplate).queryForHighlightPage(eq("books"), any(SimpleQuery.class), eq(Book.class));
    }

    @Test
    void appliesHighlightOptionsFromAnnotationToQuery() throws Exception {
      var pageable = PageRequest.of(0, 10);
      HighlightPage emptyHighlightPage = new HighlightPage<>(List.of(), pageable, 0L, List.of());
      when(solrTemplate.queryForHighlightPage(eq("books"), any(SimpleQuery.class), eq(Book.class)))
          .thenReturn(emptyHighlightPage);

      var query = createQuery("findByTitleContaining", String.class, Pageable.class);
      query.execute(new Object[]{"spring", pageable});

      var captor = ArgumentCaptor.forClass(SimpleQuery.class);
      verify(solrTemplate).queryForHighlightPage(eq("books"), captor.capture(), eq(Book.class));
      var options = captor.getValue().getHighlightOptions();
      assertThat(options).isNotNull();
      assertThat(options.getPreTag()).isEqualTo("<mark>");
      assertThat(options.getPostTag()).isEqualTo("</mark>");
      assertThat(options.getSnippets()).isEqualTo(2);
      assertThat(options.getFragsize()).isEqualTo(150);
      assertThat(options.getFields()).containsExactly("title");
    }

    @Test
    void appliesDefaultHighlightOptionsWhenNoAttributesSpecified() throws Exception {
      var pageable = PageRequest.of(0, 10);
      HighlightPage emptyHighlightPage = new HighlightPage<>(List.of(), pageable, 0L, List.of());
      when(solrTemplate.queryForHighlightPage(eq("books"), any(SimpleQuery.class), eq(Book.class)))
          .thenReturn(emptyHighlightPage);

      var query = createQuery("findByAuthor", String.class, Pageable.class);
      query.execute(new Object[]{"Picard", pageable});

      var captor = ArgumentCaptor.forClass(SimpleQuery.class);
      verify(solrTemplate).queryForHighlightPage(eq("books"), captor.capture(), eq(Book.class));
      var options = captor.getValue().getHighlightOptions();
      assertThat(options).isNotNull();
      assertThat(options.getPreTag()).isEqualTo("<em>");
      assertThat(options.getPostTag()).isEqualTo("</em>");
      assertThat(options.getSnippets()).isEqualTo(1);
      assertThat(options.getFragsize()).isEqualTo(100);
      assertThat(options.getFields()).isEmpty();
    }
  }

  @Nested
  class FacetDispatch {

    @Test
    void delegatesToQueryForFacetPageWhenReturnTypeIsFacetPage() throws Exception {
      var pageable = PageRequest.of(0, 10);
      FacetPage emptyFacetPage = new FacetPage<>(List.of(), pageable, 0L, new LinkedHashMap<>(), List.of());
      when(solrTemplate.queryForFacetPage(eq("books"), any(SimpleQuery.class), eq(Book.class)))
          .thenReturn(emptyFacetPage);

      var query = createQuery("findByTitle", String.class, Pageable.class);
      var result = query.execute(new Object[]{"spring", pageable});

      assertThat(result).isInstanceOf(FacetPage.class);
      verify(solrTemplate).queryForFacetPage(eq("books"), any(SimpleQuery.class), eq(Book.class));
    }

    @Test
    void appliesFacetOptionsFromAnnotationToQuery() throws Exception {
      var pageable = PageRequest.of(0, 10);
      FacetPage emptyFacetPage = new FacetPage<>(List.of(), pageable, 0L, new LinkedHashMap<>(), List.of());
      when(solrTemplate.queryForFacetPage(eq("books"), any(SimpleQuery.class), eq(Book.class)))
          .thenReturn(emptyFacetPage);

      var query = createQuery("findByTitle", String.class, Pageable.class);
      query.execute(new Object[]{"spring", pageable});

      var captor = ArgumentCaptor.forClass(SimpleQuery.class);
      verify(solrTemplate).queryForFacetPage(eq("books"), captor.capture(), eq(Book.class));
      var options = captor.getValue().getFacetOptions();
      assertThat(options).isNotNull();
      assertThat(options.getFacetFields()).containsExactly("author");
      assertThat(options.getFacetQueries()).containsExactly("year:[2000 TO *]");
      assertThat(options.getMinCount()).isEqualTo(2);
      assertThat(options.getLimit()).isEqualTo(20);
    }

    @Test
    void appliesDefaultFacetOptionsWhenNoAttributesSpecified() throws Exception {
      var pageable = PageRequest.of(0, 10);
      FacetPage emptyFacetPage = new FacetPage<>(List.of(), pageable, 0L, new LinkedHashMap<>(), List.of());
      when(solrTemplate.queryForFacetPage(eq("books"), any(SimpleQuery.class), eq(Book.class)))
          .thenReturn(emptyFacetPage);

      var query = createQuery("findByAuthorIs", String.class, Pageable.class);
      query.execute(new Object[]{"Picard", pageable});

      var captor = ArgumentCaptor.forClass(SimpleQuery.class);
      verify(solrTemplate).queryForFacetPage(eq("books"), captor.capture(), eq(Book.class));
      var options = captor.getValue().getFacetOptions();
      assertThat(options).isNotNull();
      assertThat(options.getMinCount()).isEqualTo(1);
      assertThat(options.getLimit()).isEqualTo(100);
      assertThat(options.getFacetFields()).isEmpty();
      assertThat(options.getFacetQueries()).isEmpty();
    }
  }
}
