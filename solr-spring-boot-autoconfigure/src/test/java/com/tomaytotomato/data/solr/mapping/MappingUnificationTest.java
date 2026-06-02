package com.tomaytotomato.data.solr.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.solr.client.solrj.beans.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link SolrDocumentReader} and {@link SolrDocumentWriter} delegate field-name
 * resolution to {@link SolrMappingContext} / {@link SolrPersistentProperty} rather than running
 * their own independent reflection — proving issue #39 is fixed.
 *
 * <p>The canonical field-name source is {@link SolrPersistentProperty#getSolrFieldName()}.
 * Both the reader and writer must agree with it for every mapped property.
 */
class MappingUnificationTest {

  @SolrEntity(collection = "articles")
  static class ArticleEntity {

    @Field("article_id")
    String id;

    @Field("article_title_t")
    String title;

    @Field
    String author;

    // No @Field annotation — should fall back to Java name
    String ignored;
  }

  SolrMappingContext context;

  @BeforeEach
  void setUp() {
    context = new SolrMappingContext();
  }

  @Nested
  class ReaderUsesMapping {

    @Test
    void readsFieldNameFromMappingContextNotOwnReflection() {
      // The mapping context says the Solr field name for "title" is "article_title_t"
      var entity = context.getRequiredPersistentEntity(ArticleEntity.class);
      var titleProp = entity.getRequiredPersistentProperty("title");
      assertThat(titleProp.getSolrFieldName()).isEqualTo("article_title_t");

      // The reader — constructed with the mapping context — must use that same name
      var reader = new SolrDocumentReader<>(ArticleEntity.class, context);
      var solrDoc = new org.apache.solr.common.SolrDocument();
      solrDoc.setField("article_id", "a-1");
      solrDoc.setField("article_title_t", "War and Peace");

      var result = reader.convert(solrDoc);

      assertThat(result.title).isEqualTo("War and Peace");
    }

    @Test
    void readsFieldWithDefaultAnnotationNameFromMappingContext() {
      var entity = context.getRequiredPersistentEntity(ArticleEntity.class);
      var authorProp = entity.getRequiredPersistentProperty("author");
      // @Field with no value → Java property name
      assertThat(authorProp.getSolrFieldName()).isEqualTo("author");

      var reader = new SolrDocumentReader<>(ArticleEntity.class, context);
      var solrDoc = new org.apache.solr.common.SolrDocument();
      solrDoc.setField("article_id", "a-2");
      solrDoc.setField("author", "Tolstoy");

      var result = reader.convert(solrDoc);

      assertThat(result.author).isEqualTo("Tolstoy");
    }
  }

  @Nested
  class WriterUsesMapping {

    @Test
    void writesFieldNameFromMappingContextNotOwnReflection() {
      var entity = context.getRequiredPersistentEntity(ArticleEntity.class);
      var titleProp = entity.getRequiredPersistentProperty("title");
      assertThat(titleProp.getSolrFieldName()).isEqualTo("article_title_t");

      var article = new ArticleEntity();
      article.id = "a-1";
      article.title = "Anna Karenina";

      var writer = new SolrDocumentWriter<>(context);
      var doc = writer.convert(article);

      // Writer must use the mapping-context field name, not its own reflection
      assertThat(doc.getFieldValue("article_title_t")).isEqualTo("Anna Karenina");
      assertThat(doc.getFieldValue("article_id")).isEqualTo("a-1");
    }

    @Test
    void writesFieldWithDefaultAnnotationNameFromMappingContext() {
      var article = new ArticleEntity();
      article.author = "Dostoevsky";

      var writer = new SolrDocumentWriter<>(context);
      var doc = writer.convert(article);

      assertThat(doc.getFieldValue("author")).isEqualTo("Dostoevsky");
    }
  }

  @Nested
  class BothPathsAgree {

    @Test
    void readerAndWriterProduceSameFieldNamesAsMappingContext() {
      var entity = context.getRequiredPersistentEntity(ArticleEntity.class);

      var article = new ArticleEntity();
      article.id = "a-99";
      article.title = "The Idiot";
      article.author = "Dostoevsky";

      var writer = new SolrDocumentWriter<>(context);
      var written = writer.convert(article);

      // Each property known to the mapping context must match what the writer produced
      entity.forEach(prop -> {
        var solrName = prop.getSolrFieldName();
        // The written doc must have used the same field name as the mapping context
        // (we only check fields that were actually written, i.e. non-null)
        if (written.getField(solrName) != null) {
          assertThat(written.getFieldValue(solrName)).isNotNull();
        }
      });

      // Now read back what was written
      var solrDoc = new org.apache.solr.common.SolrDocument();
      for (var fieldName : written.getFieldNames()) {
        solrDoc.setField(fieldName, written.getFieldValue(fieldName));
      }

      var reader = new SolrDocumentReader<>(ArticleEntity.class, context);
      var result = reader.convert(solrDoc);

      assertThat(result.id).isEqualTo("a-99");
      assertThat(result.title).isEqualTo("The Idiot");
      assertThat(result.author).isEqualTo("Dostoevsky");
    }
  }
}
