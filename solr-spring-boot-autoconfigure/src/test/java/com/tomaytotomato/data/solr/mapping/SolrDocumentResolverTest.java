package com.tomaytotomato.data.solr.mapping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolrDocumentResolverTest {

  @SolrEntity(collection = "books")
  static class BookWithExplicitCollection {}

  @SolrEntity
  static class ProductWithNoCollection {}

  @SolrEntity(collection = "${solr.books.collection}")
  static class BookWithPlaceholderCollection {}

  static class NotAnnotated {}

  @Nested
  class ResolveCollection {

    @Test
    void resolvesCollectionFromAnnotation() {
      var collection = SolrDocumentResolver.resolveCollection(BookWithExplicitCollection.class);

      assertThat(collection).isEqualTo("books");
    }

    @Test
    void fallsBackToLowercaseClassNameWhenCollectionIsEmpty() {
      var collection = SolrDocumentResolver.resolveCollection(ProductWithNoCollection.class);

      assertThat(collection).isEqualTo("productwithnocollection");
    }

    @Test
    void throwsIllegalArgumentExceptionForNonAnnotatedClass() {
      assertThatThrownBy(() -> SolrDocumentResolver.resolveCollection(NotAnnotated.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("NotAnnotated");
    }
  }

  @Nested
  class ResolveCollectionWithEnvironment {

    @Test
    void resolvesPlaceholderWhenEnvironmentHasProperty() {
      var env = new MockEnvironment().withProperty("solr.books.collection", "books-v2");

      var collection = SolrDocumentResolver.resolveCollection(BookWithPlaceholderCollection.class, env);

      assertThat(collection).isEqualTo("books-v2");
    }

    @Test
    void returnsLiteralPlaceholderWhenEnvironmentDoesNotHaveProperty() {
      var env = new MockEnvironment();

      var collection = SolrDocumentResolver.resolveCollection(BookWithPlaceholderCollection.class, env);

      assertThat(collection).isEqualTo("${solr.books.collection}");
    }

    @Test
    void literalCollectionNamePassesThroughUnchangedWithEnvironment() {
      var env = new MockEnvironment();

      var collection = SolrDocumentResolver.resolveCollection(BookWithExplicitCollection.class, env);

      assertThat(collection).isEqualTo("books");
    }

    @Test
    void fallsBackToClassNameWhenCollectionIsEmptyEvenWithEnvironment() {
      var env = new MockEnvironment();

      var collection = SolrDocumentResolver.resolveCollection(ProductWithNoCollection.class, env);

      assertThat(collection).isEqualTo("productwithnocollection");
    }

    @Test
    void nullEnvironmentFallsBackToLiteralValue() {
      var collection = SolrDocumentResolver.resolveCollection(BookWithPlaceholderCollection.class, null);

      assertThat(collection).isEqualTo("${solr.books.collection}");
    }

    @Test
    void throwsIllegalArgumentExceptionForNonAnnotatedClassWithEnvironment() {
      var env = new MockEnvironment();

      assertThatThrownBy(() -> SolrDocumentResolver.resolveCollection(NotAnnotated.class, env))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("NotAnnotated");
    }
  }

  @Nested
  class IsSolrDocument {

    @Test
    void returnsTrueForAnnotatedClass() {
      assertThat(SolrDocumentResolver.isSolrDocument(BookWithExplicitCollection.class)).isTrue();
    }

    @Test
    void returnsTrueForAnnotatedClassWithNoExplicitCollection() {
      assertThat(SolrDocumentResolver.isSolrDocument(ProductWithNoCollection.class)).isTrue();
    }

    @Test
    void returnsFalseForNonAnnotatedClass() {
      assertThat(SolrDocumentResolver.isSolrDocument(NotAnnotated.class)).isFalse();
    }
  }
}
