package com.tomaytotomato.data.solr.repository;

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
}
