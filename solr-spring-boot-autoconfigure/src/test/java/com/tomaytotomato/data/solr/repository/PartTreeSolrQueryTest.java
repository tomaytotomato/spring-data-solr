package com.tomaytotomato.data.solr.repository;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tomaytotomato.data.solr.HighlightPage;
import com.tomaytotomato.data.solr.SolrPage;
import com.tomaytotomato.data.solr.SolrTemplate;
import com.tomaytotomato.data.solr.mapping.SolrDocument;
import com.tomaytotomato.data.solr.query.SimpleQuery;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
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
class PartTreeSolrQueryTest {

  @Mock
  private SolrTemplate solrTemplate;

  @SolrDocument(collection = "products")
  static class Product {
    String id;
    String title;
    double price;
  }

  interface ProductRepository extends SolrRepository<Product> {
    List<Product> findByTitle(String title);

    Product findByTitleAndPrice(String title, double price);

    Page<Product> findByTitleContaining(String title, Pageable pageable);

    /**
     * Paged highlight method — used to test the default-page-size path by calling it
     * with {@link Pageable#unpaged()} at runtime, which Spring Data resolves as "no page".
     */
    @Highlight
    HighlightPage<Product> findHighlightByTitle(String title, Pageable pageable);

    long countByTitle(String title);

    boolean existsByTitle(String title);
  }

  private PartTreeSolrQuery createQuery(String methodName, Class<?>... paramTypes) throws Exception {
    Method method = ProductRepository.class.getMethod(methodName, paramTypes);
    var metadata = new DefaultRepositoryMetadata(ProductRepository.class);
    var factory = new SpelAwareProxyProjectionFactory();
    var queryMethod = new QueryMethod(method, metadata, factory);
    return new PartTreeSolrQuery(queryMethod, solrTemplate, method);
  }

  private PartTreeSolrQuery createQueryWithPageSize(int defaultPageSize, String methodName,
      Class<?>... paramTypes) throws Exception {
    Method method = ProductRepository.class.getMethod(methodName, paramTypes);
    var metadata = new DefaultRepositoryMetadata(ProductRepository.class);
    var factory = new SpelAwareProxyProjectionFactory();
    var queryMethod = new QueryMethod(method, metadata, factory);
    return new PartTreeSolrQuery(queryMethod, solrTemplate, method, defaultPageSize);
  }

  @Nested
  class CollectionQuery {

    @Test
    void executesDerivedListQuery() throws Exception {
      var product = new Product();
      product.title = "Spring Boot";
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of(product));

      var query = createQuery("findByTitle", String.class);
      var result = query.execute(new Object[]{"Spring Boot"});

      assertThat(result).isInstanceOf(List.class);
      assertThat((List<?>) result).hasSize(1);

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("products"), captor.capture(), eq(Product.class));
      assertThat(captor.getValue().getQuery()).isEqualTo("title:Spring\\ Boot");
    }
  }

  @Nested
  class SingleResultQuery {

    @Test
    void executesDerivedSingleResultQuery() throws Exception {
      var product = new Product();
      product.title = "Spring";
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of(product));

      var query = createQuery("findByTitleAndPrice", String.class, double.class);
      var result = query.execute(new Object[]{"Spring", 29.99});

      assertThat(result).isInstanceOf(Product.class);
      assertThat(((Product) result).title).isEqualTo("Spring");

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("products"), captor.capture(), eq(Product.class));
      assertThat(captor.getValue().getQuery()).isEqualTo("title:Spring AND price:29.99");
    }

    @Test
    void returnsNullWhenNoResults() throws Exception {
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of());

      var query = createQuery("findByTitleAndPrice", String.class, double.class);
      var result = query.execute(new Object[]{"Nothing", 0.0});

      assertThat(result).isNull();
    }
  }

  @Nested
  class PageQuery {

    @Test
    void executesDerivedPageQuery() throws Exception {
      var pageable = PageRequest.of(0, 10);
      when(solrTemplate.queryForPage(eq("products"), any(SimpleQuery.class), eq(Product.class)))
          .thenReturn(SolrPage.of(List.of(), pageable, 0, null));

      var query = createQuery("findByTitleContaining", String.class, Pageable.class);
      var result = query.execute(new Object[]{"spring", pageable});

      assertThat(result).isInstanceOf(Page.class);
      verify(solrTemplate).queryForPage(eq("products"), any(SimpleQuery.class), eq(Product.class));
    }
  }

  @Nested
  class CountQuery {

    @Test
    void executesDerivedCountQuery() throws Exception {
      when(solrTemplate.count(eq("products"), any(SimpleQuery.class)))
          .thenReturn(42L);

      var query = createQuery("countByTitle", String.class);
      var result = query.execute(new Object[]{"Spring"});

      assertThat(result).isEqualTo(42L);
    }
  }

  @Nested
  class ExistsQuery {

    @Test
    void returnsTrueWhenCountIsPositive() throws Exception {
      when(solrTemplate.count(eq("products"), any(SimpleQuery.class)))
          .thenReturn(1L);

      var query = createQuery("existsByTitle", String.class);
      var result = query.execute(new Object[]{"Spring"});

      assertThat(result).isEqualTo(true);
    }

    @Test
    void returnsFalseWhenCountIsZero() throws Exception {
      when(solrTemplate.count(eq("products"), any(SimpleQuery.class)))
          .thenReturn(0L);

      var query = createQuery("existsByTitle", String.class);
      var result = query.execute(new Object[]{"Nothing"});

      assertThat(result).isEqualTo(false);
    }
  }

  @Nested
  class DefaultPageSize {

    /**
     * The default page size path is triggered when a method accepting a {@code Pageable} is called
     * at runtime with {@link Pageable#unpaged()} — this is the realistic scenario where a caller
     * opts out of pagination explicitly, or when a framework resolves an absent pageable as unpaged.
     */

    @Test
    void appliesDefaultPageSizeOfTenWhenUnpagedPageableSupplied() throws Exception {
      HighlightPage emptyPage = new HighlightPage<>(List.of(), PageRequest.of(0, 10), 0L, List.of());
      when(solrTemplate.queryForHighlightPage(eq("products"), any(SimpleQuery.class), eq(Product.class)))
          .thenReturn(emptyPage);

      var query = createQuery("findHighlightByTitle", String.class, Pageable.class);
      query.execute(new Object[]{"Spring", Pageable.unpaged()});

      var captor = ArgumentCaptor.forClass(SimpleQuery.class);
      verify(solrTemplate).queryForHighlightPage(eq("products"), captor.capture(), eq(Product.class));
      assertThat(captor.getValue().getPageable().getPageSize()).isEqualTo(10);
      assertThat(captor.getValue().getPageable().getPageNumber()).isEqualTo(0);
    }

    @Test
    void honoursCustomDefaultPageSizeWhenUnpagedPageableSupplied() throws Exception {
      var customSize = 50;
      HighlightPage emptyPage = new HighlightPage<>(List.of(), PageRequest.of(0, customSize), 0L, List.of());
      when(solrTemplate.queryForHighlightPage(eq("products"), any(SimpleQuery.class), eq(Product.class)))
          .thenReturn(emptyPage);

      var query = createQueryWithPageSize(customSize, "findHighlightByTitle", String.class, Pageable.class);
      query.execute(new Object[]{"Spring", Pageable.unpaged()});

      var captor = ArgumentCaptor.forClass(SimpleQuery.class);
      verify(solrTemplate).queryForHighlightPage(eq("products"), captor.capture(), eq(Product.class));
      assertThat(captor.getValue().getPageable().getPageSize()).isEqualTo(customSize);
    }

    @Test
    void logsWarnWhenDefaultPageSizeIsApplied() throws Exception {
      var listAppender = new ListAppender<ILoggingEvent>();
      listAppender.start();
      var logger = (Logger) LoggerFactory.getLogger(PartTreeSolrQuery.class);
      logger.addAppender(listAppender);

      try {
        HighlightPage emptyPage = new HighlightPage<>(List.of(), PageRequest.of(0, 10), 0L, List.of());
        when(solrTemplate.queryForHighlightPage(eq("products"), any(SimpleQuery.class), eq(Product.class)))
            .thenReturn(emptyPage);

        var query = createQuery("findHighlightByTitle", String.class, Pageable.class);
        query.execute(new Object[]{"Spring", Pageable.unpaged()});

        var warnMessages = listAppender.list.stream()
            .filter(e -> e.getLevel() == Level.WARN)
            .toList();
        assertThat(warnMessages).isNotEmpty();
        assertThat(warnMessages.getFirst().getFormattedMessage())
            .contains("findHighlightByTitle")
            .contains("Pageable")
            .contains("10");
      } finally {
        logger.detachAppender(listAppender);
      }
    }

    @Test
    void doesNotLogWarnWhenExplicitPagedPageableIsProvided() throws Exception {
      var listAppender = new ListAppender<ILoggingEvent>();
      listAppender.start();
      var logger = (Logger) LoggerFactory.getLogger(PartTreeSolrQuery.class);
      logger.addAppender(listAppender);

      try {
        var pageable = PageRequest.of(0, 10);
        when(solrTemplate.queryForPage(eq("products"), any(SimpleQuery.class), eq(Product.class)))
            .thenReturn(SolrPage.of(List.of(), pageable, 0, null));

        var query = createQuery("findByTitleContaining", String.class, Pageable.class);
        query.execute(new Object[]{"spring", pageable});

        var warnMessages = listAppender.list.stream()
            .filter(e -> e.getLevel() == Level.WARN)
            .toList();
        assertThat(warnMessages).isEmpty();
      } finally {
        logger.detachAppender(listAppender);
      }
    }
  }
}
