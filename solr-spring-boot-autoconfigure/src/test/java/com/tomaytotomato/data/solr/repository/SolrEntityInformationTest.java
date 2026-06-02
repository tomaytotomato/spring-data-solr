package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.mapping.SolrEntity;
import org.apache.solr.client.solrj.beans.Field;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SolrEntityInformationTest {

  @SolrEntity(collection = "books")
  static class BookWithAnnotatedId {
    @Field
    String id;
    @Field
    String title;
  }

  @SolrEntity(collection = "books")
  static class BookWithRenamedIdField {
    @Field("id")
    String bookId;
    @Field
    String title;
  }

  @SolrEntity(collection = "books")
  static class BookWithNoIdField {
    @Field
    String title;
  }

  @Nested
  class GetId {

    @Test
    void extractsIdFromEntityWithFieldNamedId() {
      var info = new SolrEntityInformation<>(BookWithAnnotatedId.class);
      var book = new BookWithAnnotatedId();
      book.id = "book-123";

      assertThat(info.getId(book)).isEqualTo("book-123");
    }

    @Test
    void extractsIdFromEntityWhereFieldAnnotationValueIsId() {
      var info = new SolrEntityInformation<>(BookWithRenamedIdField.class);
      var book = new BookWithRenamedIdField();
      book.bookId = "book-456";

      assertThat(info.getId(book)).isEqualTo("book-456");
    }

    @Test
    void returnsNullGracefullyWhenEntityClassHasNoIdField() {
      var info = new SolrEntityInformation<>(BookWithNoIdField.class);
      var book = new BookWithNoIdField();
      book.title = "No Id Here";

      assertThat(info.getId(book)).isNull();
    }

    @Test
    void returnsNullWhenIdFieldValueIsNull() {
      var info = new SolrEntityInformation<>(BookWithAnnotatedId.class);
      var book = new BookWithAnnotatedId();
      book.id = null;

      assertThat(info.getId(book)).isNull();
    }
  }

  @Nested
  class IsNew {

    @Test
    void returnsTrueWhenIdIsNull() {
      var info = new SolrEntityInformation<>(BookWithAnnotatedId.class);
      var book = new BookWithAnnotatedId();
      book.id = null;

      assertThat(info.isNew(book)).isTrue();
    }

    @Test
    void returnsFalseWhenIdIsSet() {
      var info = new SolrEntityInformation<>(BookWithAnnotatedId.class);
      var book = new BookWithAnnotatedId();
      book.id = "book-789";

      assertThat(info.isNew(book)).isFalse();
    }
  }
}
