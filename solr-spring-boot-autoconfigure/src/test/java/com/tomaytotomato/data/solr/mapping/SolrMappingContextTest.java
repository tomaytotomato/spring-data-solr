package com.tomaytotomato.data.solr.mapping;

import org.apache.solr.client.solrj.beans.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SolrMappingContextTest {

  @SolrDocument(collection = "books")
  static class Book {
    @Id
    String id;
    @Field("book_title")
    String title;
    @Score
    Float relevanceScore;
    String author;
  }

  @SolrDocument(collection = "products")
  static class ProductWithLegacyId {
    @Field("id")
    String productId;
    @Field
    String name;
  }

  @SolrDocument
  static class Order {
    @Id
    String id;
  }

  @SolrDocument(collection = "${test.collection}")
  static class PlaceholderDocument {
    @Id
    String id;
  }

  static class NotAnnotated {
    String name;
  }

  SolrMappingContext context;

  @BeforeEach
  void setUp() {
    context = new SolrMappingContext();
  }

  @Nested
  class EntityLookup {

    @Test
    void returnsEntityForAnnotatedClass() {
      assertThat(context.getPersistentEntity(Book.class)).isNotNull();
    }

    @Test
    void returnsNullForNonAnnotatedClass() {
      assertThat(context.getPersistentEntity(NotAnnotated.class)).isNull();
    }

    @Test
    void returnsSameInstanceOnRepeatedLookup() {
      var first = context.getRequiredPersistentEntity(Book.class);
      var second = context.getRequiredPersistentEntity(Book.class);

      assertThat(first).isSameAs(second);
    }
  }

  @Nested
  class CollectionResolution {

    @Test
    void usesExplicitCollectionFromAnnotation() {
      var entity = context.getRequiredPersistentEntity(Book.class);

      assertThat(entity.getCollection()).isEqualTo("books");
    }

    @Test
    void fallsBackToLowercasedClassNameWhenCollectionNotSet() {
      var entity = context.getRequiredPersistentEntity(Order.class);

      assertThat(entity.getCollection()).isEqualTo("order");
    }
  }

  @Nested
  class IdProperty {

    @Test
    void resolvesIdFromSpringDataIdAnnotation() {
      var entity = context.getRequiredPersistentEntity(Book.class);

      assertThat(entity.getIdProperty()).isNotNull();
      assertThat(entity.getIdProperty().getName()).isEqualTo("id");
    }

    @Test
    void resolvesIdFromSolrJFieldConvention() {
      var entity = context.getRequiredPersistentEntity(ProductWithLegacyId.class);

      assertThat(entity.getIdProperty()).isNotNull();
      assertThat(entity.getIdProperty().getName()).isEqualTo("productId");
    }
  }

  @Nested
  class PropertyMapping {

    @Test
    void usesSolrFieldNameFromAnnotation() {
      var entity = context.getRequiredPersistentEntity(Book.class);
      var property = entity.getRequiredPersistentProperty("title");

      assertThat(property.getSolrFieldName()).isEqualTo("book_title");
    }

    @Test
    void fallsBackToJavaPropertyNameWhenNoAnnotation() {
      var entity = context.getRequiredPersistentEntity(Book.class);
      var property = entity.getRequiredPersistentProperty("author");

      assertThat(property.getSolrFieldName()).isEqualTo("author");
    }

    @Test
    void identifiesScoreProperty() {
      var entity = context.getRequiredPersistentEntity(Book.class);
      var property = entity.getRequiredPersistentProperty("relevanceScore");

      assertThat(property.isScoreProperty()).isTrue();
    }

    @Test
    void nonScorePropertyIsNotScore() {
      var entity = context.getRequiredPersistentEntity(Book.class);
      var property = entity.getRequiredPersistentProperty("title");

      assertThat(property.isScoreProperty()).isFalse();
    }
  }

  @Nested
  class PlaceholderCollectionResolution {

    @Test
    void resolvesPlaceholderCollectionWhenEnvironmentHasProperty() {
      var env = new MockEnvironment().withProperty("test.collection", "resolved-collection");
      var contextWithEnv = new SolrMappingContext(env);

      var entity = contextWithEnv.getRequiredPersistentEntity(PlaceholderDocument.class);

      assertThat(entity.getCollection()).isEqualTo("resolved-collection");
    }

    @Test
    void returnsLiteralPlaceholderWhenPropertyNotDefinedInEnvironment() {
      var env = new MockEnvironment();
      var contextWithEnv = new SolrMappingContext(env);

      var entity = contextWithEnv.getRequiredPersistentEntity(PlaceholderDocument.class);

      assertThat(entity.getCollection()).isEqualTo("${test.collection}");
    }

    @Test
    void literalCollectionNamePassesThroughUnchangedWhenEnvironmentPresent() {
      var env = new MockEnvironment().withProperty("test.collection", "resolved-collection");
      var contextWithEnv = new SolrMappingContext(env);

      var entity = contextWithEnv.getRequiredPersistentEntity(Book.class);

      assertThat(entity.getCollection()).isEqualTo("books");
    }

    @Test
    void noEnvironmentConstructorLeavesMappingContextFunctional() {
      var entity = context.getRequiredPersistentEntity(Book.class);

      assertThat(entity.getCollection()).isEqualTo("books");
    }
  }
}
