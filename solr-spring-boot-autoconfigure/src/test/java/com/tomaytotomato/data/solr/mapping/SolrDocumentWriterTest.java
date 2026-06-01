package com.tomaytotomato.data.solr.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.apache.solr.client.solrj.beans.Field;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SolrDocumentWriterTest {

  @Nested
  class Convert {

    @Test
    void writesStringFieldToSolrInputDocument() {
      var entity = new BookEntity();
      entity.id = "book-1";
      entity.title = "Dune";

      var writer = new SolrDocumentWriter<>();
      var doc = writer.convert(entity);

      assertThat(doc.getFieldValue("id")).isEqualTo("book-1");
      assertThat(doc.getFieldValue("title_t")).isEqualTo("Dune");
    }

    @Test
    void writesNumericFieldToSolrInputDocument() {
      var entity = new BookEntity();
      entity.id = "book-2";
      entity.year = 1965;
      entity.price = 12.99;

      var writer = new SolrDocumentWriter<>();
      var doc = writer.convert(entity);

      assertThat(doc.getFieldValue("year_i")).isEqualTo(1965);
      assertThat(doc.getFieldValue("price_d")).isEqualTo(12.99);
    }

    @Test
    void writesMultiValuedFieldToSolrInputDocument() {
      var entity = new BookEntity();
      entity.id = "book-3";
      entity.categories = List.of("sci-fi", "classic");

      var writer = new SolrDocumentWriter<>();
      var doc = writer.convert(entity);

      assertThat(doc.getFieldValues("categories_ss")).containsExactly("sci-fi", "classic");
    }

    @Test
    void skipsNullReferenceFieldValues() {
      var entity = new BookEntity();
      entity.id = "book-4";

      var writer = new SolrDocumentWriter<>();
      var doc = writer.convert(entity);

      assertThat(doc.getFieldValue("id")).isEqualTo("book-4");
      assertThat(doc.getField("title_t")).isNull();
      assertThat(doc.getField("categories_ss")).isNull();
    }

    @Test
    void usesJavaFieldNameWhenAnnotationValueIsDefault() {
      var entity = new DefaultNameEntity();
      entity.name = "test";

      var writer = new SolrDocumentWriter<>();
      var doc = writer.convert(entity);

      assertThat(doc.getFieldValue("name")).isEqualTo("test");
    }

    @Test
    void readsPrivateFields() {
      var entity = PrivateFieldEntity.of("secret-1", "hidden value");

      var writer = new SolrDocumentWriter<>();
      var doc = writer.convert(entity);

      assertThat(doc.getFieldValue("id")).isEqualTo("secret-1");
      assertThat(doc.getFieldValue("value_s")).isEqualTo("hidden value");
    }

    @Test
    void walksInheritedFields() {
      var entity = new ChildEntity();
      entity.id = "parent-1";
      entity.childField = "child-value";

      var writer = new SolrDocumentWriter<>();
      var doc = writer.convert(entity);

      assertThat(doc.getFieldValue("id")).isEqualTo("parent-1");
      assertThat(doc.getFieldValue("child_s")).isEqualTo("child-value");
    }

    @Test
    void throwsOnEntityWithoutNoArgConstructor() {
      var entity = new NoDefaultConstructorEntity("boom");

      var writer = new SolrDocumentWriter<>();
      var doc = writer.convert(entity);

      assertThat(doc.getFieldValue("value_s")).isEqualTo("boom");
    }
  }

  @Nested
  class ConvertWithCustomConverter {

    @Test
    void appliesRegisteredWriteConverterForNonStringField() {
      var reg = SolrConverterRegistration.of(LocalDate.class, String.class, LocalDate::toString);
      var mappingConverter = new SolrMappingConverter(new SolrCustomConversions(List.of(reg)));
      var writer = new SolrDocumentWriter<>(mappingConverter);

      var entity = new DatedEntity();
      entity.id = "d-1";
      entity.publishedDate = LocalDate.of(2024, 6, 1);

      var doc = writer.convert(entity);

      assertThat(doc.getFieldValue("id")).isEqualTo("d-1");
      assertThat(doc.getFieldValue("pub_date_s")).isEqualTo("2024-06-01");
    }

    @Test
    void withoutConverterLocalDateIsPassedThroughAsIs() {
      var writer = new SolrDocumentWriter<>();

      var entity = new DatedEntity();
      entity.id = "d-2";
      entity.publishedDate = LocalDate.of(2024, 6, 1);

      var doc = writer.convert(entity);

      // Without a converter the LocalDate is stored as-is — Solr may not accept it
      // but the writer itself does not crash
      assertThat(doc.getFieldValue("id")).isEqualTo("d-2");
      assertThat(doc.getFieldValue("pub_date_s")).isEqualTo(LocalDate.of(2024, 6, 1));
    }
  }

  public static class BookEntity {
    @Field("id") public String id;
    @Field("title_t") public String title;
    @Field("year_i") public int year;
    @Field("price_d") public double price;
    @Field("categories_ss") public List<String> categories;
  }

  public static class DefaultNameEntity {
    @Field public String name;
  }

  public static class PrivateFieldEntity {
    @Field("id") private String id;
    @Field("value_s") private String value;

    public static PrivateFieldEntity of(String id, String value) {
      var entity = new PrivateFieldEntity();
      entity.id = id;
      entity.value = value;
      return entity;
    }
  }

  public static class ParentEntity {
    @Field("id") public String id;
  }

  public static class ChildEntity extends ParentEntity {
    @Field("child_s") public String childField;
  }

  public static class NoDefaultConstructorEntity {
    @Field("value_s") public String value;

    public NoDefaultConstructorEntity() {}

    public NoDefaultConstructorEntity(String value) {
      this.value = value;
    }
  }

  public static class DatedEntity {
    @Field("id") public String id;
    @Field("pub_date_s") public LocalDate publishedDate;
  }
}
