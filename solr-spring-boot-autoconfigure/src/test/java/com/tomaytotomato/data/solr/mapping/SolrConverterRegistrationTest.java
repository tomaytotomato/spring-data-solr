package com.tomaytotomato.data.solr.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SolrConverterRegistrationTest {

  @Nested
  class Factory {

    @Test
    void ofCreatesRegistrationWithCorrectTypes() {
      SolrDocumentConverter<String, LocalDate> fn = LocalDate::parse;

      var reg = SolrConverterRegistration.of(String.class, LocalDate.class, fn);

      assertThat(reg.getSourceType()).isEqualTo(String.class);
      assertThat(reg.getTargetType()).isEqualTo(LocalDate.class);
    }

    @Test
    void converterIsAccessibleAndFunctional() {
      var reg = SolrConverterRegistration.of(String.class, LocalDate.class, LocalDate::parse);

      var result = reg.getConverter().convert("2024-06-01");

      assertThat(result).isEqualTo(LocalDate.of(2024, 6, 1));
    }
  }
}
