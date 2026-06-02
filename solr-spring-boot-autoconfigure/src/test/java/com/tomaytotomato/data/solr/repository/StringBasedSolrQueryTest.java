package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.SolrTemplate;
import com.tomaytotomato.data.solr.mapping.SolrEntity;
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
import org.springframework.data.repository.query.Param;
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

  @SolrEntity(collection = "products")
  static class Product {
    String id;
    String title;
    String author;
    double price;
  }

  interface TestRepository extends SolrRepository<Product> {

    // --- positional params ---

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

    // --- named params with @Param ---

    @Query("title::title AND author::author")
    List<Product> findByTitleAndAuthorNamed(
        @Param("title") String title,
        @Param("author") String author);

    @Query("title::title")
    List<Product> findByTitleNamed(@Param("title") String t);

    @Query(value = "author::author", count = true)
    long countByAuthorNamed(@Param("author") String author);

    // --- named params relying on compiled parameter names (no @Param) ---

    @Query("title::title AND author::author")
    List<Product> findByTitleAndAuthorReflection(String title, String author);

    // --- mixed: Solr field:value syntax beside a named param ---

    @Query("status:active AND author::author")
    List<Product> findActiveByAuthor(@Param("author") String author);

    // --- edge-case: ?N inside a quoted string ---

    @Query("title:\"?0\" AND author:?1")
    List<Product> findByQuotedTitleAndAuthor(String title, String author);
  }

  private StringBasedSolrQuery createQuery(String methodName, Class<?>... paramTypes) throws Exception {
    Method method = TestRepository.class.getMethod(methodName, paramTypes);
    var metadata = new DefaultRepositoryMetadata(TestRepository.class);
    var factory = new SpelAwareProxyProjectionFactory();
    var queryMethod = new QueryMethod(method, metadata, factory);
    var annotation = method.getAnnotation(Query.class);
    return new StringBasedSolrQuery(queryMethod, solrTemplate, annotation.value(), annotation.count(), method);
  }

  // ============================================================
  // Positional parameter substitution (backwards-compatible)
  // ============================================================

  @Nested
  class PositionalParameterSubstitution {

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

  // ============================================================
  // Named parameter substitution — @Param annotation
  // ============================================================

  @Nested
  class NamedParameterWithParamAnnotation {

    @Test
    void substitutesNamedParameterAnnotatedWithParam() throws Exception {
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of());

      var query = createQuery("findByTitleNamed", String.class);
      query.execute(new Object[]{"Spring"});

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("products"), captor.capture(), eq(Product.class));
      assertThat(captor.getValue().getQuery()).isEqualTo("title:Spring");
    }

    @Test
    void substitutesBothNamedParametersAnnotatedWithParam() throws Exception {
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of());

      var query = createQuery("findByTitleAndAuthorNamed", String.class, String.class);
      query.execute(new Object[]{"Spring", "Picard"});

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("products"), captor.capture(), eq(Product.class));
      assertThat(captor.getValue().getQuery()).isEqualTo("title:Spring AND author:Picard");
    }

    @Test
    void escapesSpecialCharactersInNamedParameterValue() throws Exception {
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of());

      var query = createQuery("findByTitleNamed", String.class);
      query.execute(new Object[]{"spring(boot)"});

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("products"), captor.capture(), eq(Product.class));
      assertThat(captor.getValue().getQuery()).isEqualTo("title:spring\\(boot\\)");
    }

    @Test
    void countQueryWithNamedParameter() throws Exception {
      when(solrTemplate.count(eq("products"), any(SolrQuery.class)))
          .thenReturn(3L);

      var query = createQuery("countByAuthorNamed", String.class);
      var result = query.execute(new Object[]{"Picard"});

      assertThat(result).isEqualTo(3L);
      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).count(eq("products"), captor.capture());
      assertThat(captor.getValue().getQuery()).isEqualTo("author:Picard");
    }
  }

  // ============================================================
  // Named parameter substitution — compiled parameter names
  // ============================================================

  @Nested
  class NamedParameterFromCompiledName {

    @Test
    void substitutesUsingCompiledParameterNamesWhenParamAnnotationAbsent() throws Exception {
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of());

      var query = createQuery("findByTitleAndAuthorReflection", String.class, String.class);
      query.execute(new Object[]{"Spring", "Picard"});

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("products"), captor.capture(), eq(Product.class));
      assertThat(captor.getValue().getQuery()).isEqualTo("title:Spring AND author:Picard");
    }
  }

  // ============================================================
  // Field:value syntax must not be corrupted by named params
  // ============================================================

  @Nested
  class FieldValueSyntaxPreserved {

    @Test
    void solrFieldReferenceIsNotCorruptedWhenNamedParamAlsoPresent() throws Exception {
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of());

      var query = createQuery("findActiveByAuthor", String.class);
      query.execute(new Object[]{"Picard"});

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("products"), captor.capture(), eq(Product.class));
      // "status:active" must survive intact; only ":author" (preceded by space) is substituted
      assertThat(captor.getValue().getQuery()).isEqualTo("status:active AND author:Picard");
    }
  }

  // ============================================================
  // Edge case: ?N literal inside a quoted string
  // ============================================================

  @Nested
  class PositionalParamInsideQuotedString {

    /**
     * Documents a known limitation: a {@code ?N} token inside a quoted string is replaced by the
     * positional substitution pass because the replacement is purely textual and does not parse
     * Solr syntax. Named parameters should be preferred to avoid this ambiguity.
     *
     * <p>This test documents the <em>current</em> (broken) behaviour rather than asserting a
     * correct result, so that any future fix is immediately visible as a test promotion.
     */
    @Test
    void positionalTokenInsideQuotedStringIsSubstituted_knownLimitation() throws Exception {
      when(solrTemplate.query(eq("products"), any(SolrQuery.class), eq(Product.class)))
          .thenReturn(List.of());

      var query = createQuery("findByQuotedTitleAndAuthor", String.class, String.class);
      query.execute(new Object[]{"Spring", "Picard"});

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("products"), captor.capture(), eq(Product.class));

      // The ?0 token inside the quoted string is naively replaced — this is the known limitation.
      // A correct implementation would leave the literal ?0 inside quotes untouched.
      // If this assertion starts failing it means the limitation has been fixed — remove this
      // comment and update the assertion to the correct expected value.
      assertThat(captor.getValue().getQuery())
          .as("?N inside a quoted string is corrupted by positional substitution (known limitation)")
          .isEqualTo("title:\"Spring\" AND author:Picard");
    }
  }

  // ============================================================
  // Return-type dispatch
  // ============================================================

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

  // ============================================================
  // Injection prevention
  // ============================================================

  @Nested
  class InjectionPrevention {

    @Test
    void escapesLuceneSpecialCharactersInPositionalParameter() throws Exception {
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

  // ============================================================
  // Count query
  // ============================================================

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
