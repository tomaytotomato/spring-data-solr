package com.tomaytotomato.data.solr.repository;

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
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.query.QueryMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StringBasedSolrQueryTest {

  @Mock
  private SolrTemplate solrTemplate;

  @SolrDocument(collection = "products")
  static class Product {
    String id;
    String title;
    String author;
    double price;
  }

  interface TestRepository extends SolrRepository<Product> {

    @Query("title:?0")
    List<Product> findByTitleCustom(String title);

    @Query("title:?0 AND author:?1")
    List<Product> findByTitleAndAuthorCustom(String title, String author);

    @Query("*:*")
    List<Product> findAllCustom();

    @Query("price:[?0 TO ?1]")
    List<Product> findByPriceRangeCustom(double low, double high);

    @Query("title:?0")
    Product findSingleByTitleCustom(String title);

    @Query(value = "author:?0", count = true)
    long countByAuthorCustom(String author);

    @Query("title:?0")
    List<Product> findByTitleInjection(String title);
  }

  private StringBasedSolrQuery createQuery(String methodName, Class<?>... paramTypes) throws Exception {
    Method method = TestRepository.class.getMethod(methodName, paramTypes);
    var metadata = new DefaultRepositoryMetadata(TestRepository.class);
    var factory = new SpelAwareProxyProjectionFactory();
    var queryMethod = new QueryMethod(method, metadata, factory);
    var annotation = method.getAnnotation(Query.class);
    return new StringBasedSolrQuery(queryMethod, solrTemplate, annotation.value(), annotation.count(), method);
  }

  @Nested
  class SingleParameterSubstitution {

    @Test
    void substitutesFirstParameterIntoQueryString() throws Exception {
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of());

      var query = createQuery("findByTitleCustom", String.class);
      query.execute(new Object[]{"Spring"});

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("products"), captor.capture(), eq(Product.class));
      assertThat(captor.getValue().getQuery()).isEqualTo("title:Spring");
    }
  }

  @Nested
  class MultipleParameterSubstitution {

    @Test
    void substitutesBothParametersIntoQueryString() throws Exception {
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of());

      var query = createQuery("findByTitleAndAuthorCustom", String.class, String.class);
      query.execute(new Object[]{"Spring", "Picard"});

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("products"), captor.capture(), eq(Product.class));
      assertThat(captor.getValue().getQuery()).isEqualTo("title:Spring AND author:Picard");
    }
  }

  @Nested
  class NoParameterQuery {

    @Test
    void passesQueryStringUnchangedWhenNoParameters() throws Exception {
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of());

      var query = createQuery("findAllCustom");
      query.execute(new Object[]{});

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("products"), captor.capture(), eq(Product.class));
      assertThat(captor.getValue().getQuery()).isEqualTo("*:*");
    }
  }

  @Nested
  class RangeParameterSubstitution {

    @Test
    void substitutesNumericParametersIntoRangeQuery() throws Exception {
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of());

      var query = createQuery("findByPriceRangeCustom", double.class, double.class);
      query.execute(new Object[]{10.0, 50.0});

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("products"), captor.capture(), eq(Product.class));
      assertThat(captor.getValue().getQuery()).isEqualTo("price:[10.0 TO 50.0]");
    }
  }

  @Nested
  class CollectionReturnType {

    @Test
    void returnsListOfResultsFromSolrTemplate() throws Exception {
      var product = new Product();
      product.title = "Spring Boot";
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of(product));

      var query = createQuery("findByTitleCustom", String.class);
      var result = query.execute(new Object[]{"Spring Boot"});

      assertThat(result).isInstanceOf(List.class);
      assertThat((List<?>) result).hasSize(1);
    }
  }

  @Nested
  class SingleReturnType {

    @Test
    void returnsFirstResultWhenQueryReturnsSingleEntity() throws Exception {
      var product = new Product();
      product.title = "Spring";
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of(product));

      var query = createQuery("findSingleByTitleCustom", String.class);
      var result = query.execute(new Object[]{"Spring"});

      assertThat(result).isInstanceOf(Product.class);
      assertThat(((Product) result).title).isEqualTo("Spring");
    }

    @Test
    void returnsNullWhenNoResultsForSingleEntityQuery() throws Exception {
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of());

      var query = createQuery("findSingleByTitleCustom", String.class);
      var result = query.execute(new Object[]{"Nothing"});

      assertThat(result).isNull();
    }
  }

  @Nested
  class InjectionPrevention {

    @Test
    void escapesLuceneSpecialCharactersInParameter() throws Exception {
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of());

      var query = createQuery("findByTitleInjection", String.class);
      query.execute(new Object[]{"spring(boot)"});

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("products"), captor.capture(), eq(Product.class));
      assertThat(captor.getValue().getQuery()).isEqualTo("title:spring\\(boot\\)");
    }

    @Test
    void injectionPayloadDoesNotProduceMatchAllQuery() throws Exception {
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of());

      var query = createQuery("findByTitleInjection", String.class);
      query.execute(new Object[]{"\") OR (*:*"});

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("products"), captor.capture(), eq(Product.class));
      assertThat(captor.getValue().getQuery()).doesNotContain("*:*");
    }

    @Test
    void normalStringParameterWorksCorrectlyAfterEscaping() throws Exception {
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of());

      var query = createQuery("findByTitleInjection", String.class);
      query.execute(new Object[]{"SpringBoot"});

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("products"), captor.capture(), eq(Product.class));
      assertThat(captor.getValue().getQuery()).isEqualTo("title:SpringBoot");
    }
  }

  @Nested
  class CountQuery {

    @Test
    void delegatesToSolrTemplateCountWhenCountAttributeIsTrue() throws Exception {
      when(solrTemplate.count(eq("products"), any(SolrQuery.class)))
          .thenReturn(7L);

      var query = createQuery("countByAuthorCustom", String.class);
      var result = query.execute(new Object[]{"Picard"});

      assertThat(result).isEqualTo(7L);
      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).count(eq("products"), captor.capture());
      assertThat(captor.getValue().getQuery()).isEqualTo("author:Picard");
    }
  }
}
