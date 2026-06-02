package com.tomaytotomato.data.solr.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.common.SolrDocument;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SolrDocumentReaderTest {

  @Nested
  class Convert {

    @Test
    void mapsStringFieldFromSolrDocument() {
      var reader = new SolrDocumentReader<>(BookEntity.class);
      var solrDoc = new SolrDocument();
      solrDoc.setField("id", "book-1");
      solrDoc.setField("title_t", "Dune");

      var result = reader.convert(solrDoc);

      assertThat(result.id).isEqualTo("book-1");
      assertThat(result.title).isEqualTo("Dune");
    }

    @Test
    void mapsArrayListOfDoublesToStringForSpatialField() {
      var reader = new SolrDocumentReader<>(BookEntity.class);
      var solrDoc = new SolrDocument();
      solrDoc.setField("id", "book-1");
      solrDoc.setField("location", new ArrayList<>(List.of(40.7484, -73.9856)));

      var result = reader.convert(solrDoc);

      assertThat(result.location).isEqualTo("40.7484,-73.9856");
    }

    @Test
    void mapsMultiValuedFieldToList() {
      var reader = new SolrDocumentReader<>(BookEntity.class);
      var solrDoc = new SolrDocument();
      solrDoc.setField("id", "book-1");
      solrDoc.setField("categories_ss", new ArrayList<>(List.of("sci-fi", "classic")));

      var result = reader.convert(solrDoc);

      assertThat(result.categories).containsExactly("sci-fi", "classic");
    }

    @Test
    void leavesUnmappedFieldsAsDefaults() {
      var reader = new SolrDocumentReader<>(BookEntity.class);
      var solrDoc = new SolrDocument();
      solrDoc.setField("id", "book-1");

      var result = reader.convert(solrDoc);

      assertThat(result.id).isEqualTo("book-1");
      assertThat(result.title).isNull();
      assertThat(result.location).isNull();
      assertThat(result.categories).isNull();
    }

    @Test
    void incompatibleFieldWithoutConverterIsLeftNull() {
      var reader = new SolrDocumentReader<>(DatedEntity.class);
      var solrDoc = new SolrDocument();
      solrDoc.setField("id", "d-1");
      solrDoc.setField("pub_date_s", "2024-06-01");

      var result = reader.convert(solrDoc);

      assertThat(result.id).isEqualTo("d-1");
      assertThat(result.publishedDate).isNull();
    }
  }

  @Nested
  class ConvertWithCustomConverter {

    @Test
    void appliesRegisteredReadConverterForIncompatibleField() {
      var reg = SolrConverterRegistration.of(String.class, LocalDate.class, LocalDate::parse);
      var mappingConverter = new SolrMappingConverter(new SolrCustomConversions(List.of(reg)));
      var reader = new SolrDocumentReader<>(DatedEntity.class, mappingConverter);
      var solrDoc = new SolrDocument();
      solrDoc.setField("id", "d-2");
      solrDoc.setField("pub_date_s", "2024-06-01");

      var result = reader.convert(solrDoc);

      assertThat(result.id).isEqualTo("d-2");
      assertThat(result.publishedDate).isEqualTo(LocalDate.of(2024, 6, 1));
    }
  }

  public static class BookEntity {

    @Field("id")
    public String id;

    @Field("title_t")
    public String title;

    @Field("location")
    public String location;

    @Field("categories_ss")
    public List<String> categories;
  }

  public static class DatedEntity {

    @Field("id")
    public String id;

    @Field("pub_date_s")
    public LocalDate publishedDate;
  }
}
