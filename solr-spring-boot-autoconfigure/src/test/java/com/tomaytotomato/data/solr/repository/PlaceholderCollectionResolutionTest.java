package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.SolrTemplate;
import com.tomaytotomato.data.solr.mapping.SolrEntity;
import com.tomaytotomato.data.solr.mapping.SolrDocumentResolver;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.solr.client.solrj.beans.Field;
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
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@code ${placeholder}} values in {@link SolrEntity#collection()} are resolved
 * against a Spring {@link org.springframework.core.env.Environment} throughout the repository
 * layer — specifically via {@link SolrEntityInformation}, {@link PartTreeSolrQuery},
 * {@link StringBasedSolrQuery}, and {@link SimpleSolrRepository}.
 *
 * <p>Before the fix, all of these callers used the no-environment static overload of
 * {@link SolrDocumentResolver}, so placeholders silently returned their literal text.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class PlaceholderCollectionResolutionTest {

  @Mock
  private SolrTemplate solrTemplate;

  @SolrEntity(collection = "${solr.article.collection}")
  static class Article {
    @Field
    String id;
    @Field
    String title;
  }

  interface ArticleRepository extends SolrRepository<Article> {
    List<Article> findByTitle(String title);

    @Query("title:?0")
    List<Article> searchByTitle(String title);
  }

  static MockEnvironment envWith(String propertyName, String value) {
    return new MockEnvironment().withProperty(propertyName, value);
  }

  // -------------------------------------------------------------------------
  // SolrDocumentResolver (bean form) — instance-level resolution
  // -------------------------------------------------------------------------

  @Nested
  class SolrDocumentResolverBean {

    @Test
    void resolvesPlaceholderWhenEnvironmentContainsProperty() {
      var env = envWith("solr.article.collection", "articles-v2");
      var resolver = new SolrDocumentResolver(env);

      var collection = resolver.resolve(Article.class);

      assertThat(collection).isEqualTo("articles-v2");
    }

    @Test
    void returnsLiteralWhenEnvironmentLacksProperty() {
      var resolver = new SolrDocumentResolver(new MockEnvironment());

      var collection = resolver.resolve(Article.class);

      assertThat(collection).isEqualTo("${solr.article.collection}");
    }
  }

  // -------------------------------------------------------------------------
  // SolrEntityInformation — collection name at construction time
  // -------------------------------------------------------------------------

  @Nested
  class EntityInformationPlaceholderResolution {

    @Test
    void resolvesPlaceholderCollectionFromEnvironment() {
      var env = envWith("solr.article.collection", "articles-prod");
      var resolver = new SolrDocumentResolver(env);

      var info = new SolrEntityInformation<>(Article.class, resolver);

      assertThat(info.getCollection()).isEqualTo("articles-prod");
    }

    @Test
    void returnsLiteralPlaceholderWhenPropertyIsNotSet() {
      var resolver = new SolrDocumentResolver(new MockEnvironment());

      var info = new SolrEntityInformation<>(Article.class, resolver);

      assertThat(info.getCollection()).isEqualTo("${solr.article.collection}");
    }
  }

  // -------------------------------------------------------------------------
  // SimpleSolrRepository — delegates save/query using resolved collection
  // -------------------------------------------------------------------------

  @Nested
  class SimpleSolrRepositoryPlaceholderResolution {

    @Test
    void usesResolvedCollectionNameForSave() {
      var env = envWith("solr.article.collection", "articles-staging");
      var resolver = new SolrDocumentResolver(env);
      var repo = new SimpleSolrRepository<>(solrTemplate, Article.class, resolver);
      var article = new Article();
      article.id = "1";
      when(solrTemplate.save("articles-staging", article)).thenReturn(article);

      repo.save(article);

      verify(solrTemplate).save("articles-staging", article);
    }

    @Test
    void usesLiteralPlaceholderWhenPropertyNotSetForSave() {
      var resolver = new SolrDocumentResolver(new MockEnvironment());
      var repo = new SimpleSolrRepository<>(solrTemplate, Article.class, resolver);
      var article = new Article();
      article.id = "1";
      when(solrTemplate.save("${solr.article.collection}", article)).thenReturn(article);

      repo.save(article);

      verify(solrTemplate).save("${solr.article.collection}", article);
    }
  }

  // -------------------------------------------------------------------------
  // PartTreeSolrQuery — derived query method resolution
  // -------------------------------------------------------------------------

  @Nested
  class PartTreeSolrQueryPlaceholderResolution {

    private PartTreeSolrQuery createQueryWithResolver(String methodName, SolrDocumentResolver resolver,
        Class<?>... paramTypes) throws Exception {
      var method = ArticleRepository.class.getMethod(methodName, paramTypes);
      var metadata = new DefaultRepositoryMetadata(ArticleRepository.class);
      var factory = new SpelAwareProxyProjectionFactory();
      var queryMethod = new QueryMethod(method, metadata, factory);
      return new PartTreeSolrQuery(queryMethod, solrTemplate, method, resolver);
    }

    @Test
    void routesDerivedQueryToResolvedCollection() throws Exception {
      var env = envWith("solr.article.collection", "articles-live");
      var resolver = new SolrDocumentResolver(env);
      when(solrTemplate.query(eq("articles-live"), any(SolrQuery.class), eq(Article.class)))
          .thenReturn(List.of());

      var query = createQueryWithResolver("findByTitle", resolver, String.class);
      query.execute(new Object[]{"Spring"});

      verify(solrTemplate).query(eq("articles-live"), any(SolrQuery.class), eq(Article.class));
    }

    @Test
    void routesDerivedQueryToLiteralWhenPropertyNotSet() throws Exception {
      var resolver = new SolrDocumentResolver(new MockEnvironment());
      when(solrTemplate.query(eq("${solr.article.collection}"), any(SolrQuery.class), eq(Article.class)))
          .thenReturn(List.of());

      var query = createQueryWithResolver("findByTitle", resolver, String.class);
      query.execute(new Object[]{"Spring"});

      verify(solrTemplate).query(eq("${solr.article.collection}"), any(SolrQuery.class), eq(Article.class));
    }
  }

  // -------------------------------------------------------------------------
  // StringBasedSolrQuery — @Query annotation method resolution
  // -------------------------------------------------------------------------

  @Nested
  class StringBasedSolrQueryPlaceholderResolution {

    private StringBasedSolrQuery createQueryWithResolver(String methodName, SolrDocumentResolver resolver,
        Class<?>... paramTypes) throws Exception {
      var method = ArticleRepository.class.getMethod(methodName, paramTypes);
      var annotation = method.getAnnotation(Query.class);
      var metadata = new DefaultRepositoryMetadata(ArticleRepository.class);
      var factory = new SpelAwareProxyProjectionFactory();
      var queryMethod = new QueryMethod(method, metadata, factory);
      return new StringBasedSolrQuery(queryMethod, solrTemplate, annotation.value(),
          annotation.count(), method, resolver);
    }

    @Test
    void routesAnnotatedQueryToResolvedCollection() throws Exception {
      var env = envWith("solr.article.collection", "articles-live");
      var resolver = new SolrDocumentResolver(env);
      when(solrTemplate.query(eq("articles-live"), any(SolrQuery.class), eq(Article.class)))
          .thenReturn(List.of());

      var query = createQueryWithResolver("searchByTitle", resolver, String.class);
      query.execute(new Object[]{"Solr"});

      var captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrTemplate).query(eq("articles-live"), captor.capture(), eq(Article.class));
    }

    @Test
    void routesAnnotatedQueryToLiteralWhenPropertyNotSet() throws Exception {
      var resolver = new SolrDocumentResolver(new MockEnvironment());
      when(solrTemplate.query(eq("${solr.article.collection}"), any(SolrQuery.class), eq(Article.class)))
          .thenReturn(List.of());

      var query = createQueryWithResolver("searchByTitle", resolver, String.class);
      query.execute(new Object[]{"Solr"});

      verify(solrTemplate).query(eq("${solr.article.collection}"), any(SolrQuery.class), eq(Article.class));
    }
  }
}
