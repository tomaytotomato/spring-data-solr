package com.tomaytotomato.data.solr.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SolrMappingConverterTest {

  @Nested
  class DefaultConstructor {

    @Test
    void defaultConstructorCreatesEmptyConversions() {
      var converter = new SolrMappingConverter();

      assertThat(converter.getConversions().getConverters()).isEmpty();
    }

    @Test
    void canConvertReturnsFalseWhenNoConvertersRegistered() {
      var converter = new SolrMappingConverter();

      assertThat(converter.canConvert(String.class, LocalDate.class)).isFalse();
    }
  }

  @Nested
  class CustomConversions {

    @Test
    void customConversionsAreAccessible() {
      SolrDocumentConverter<String, LocalDate> readConverter = LocalDate::parse;
      var conversions = new SolrCustomConversions(List.of(readConverter));

      var converter = new SolrMappingConverter(conversions);

      assertThat(converter.getConversions()).isSameAs(conversions);
    }

    @Test
    void convertersFromCustomConversionsAreRetrievable() {
      SolrDocumentConverter<String, LocalDate> readConverter = LocalDate::parse;
      var conversions = new SolrCustomConversions(List.of(readConverter));

      var converter = new SolrMappingConverter(conversions);

      assertThat(converter.getConversions().getConverters()).hasSize(1);
    }
  }

  @Nested
  class TypedConversionDispatch {

    @Test
    void canConvertReturnsTrueForRegisteredTypePair() {
      var reg = SolrConverterRegistration.of(String.class, LocalDate.class, LocalDate::parse);
      var converter = new SolrMappingConverter(new SolrCustomConversions(List.of(reg)));

      assertThat(converter.canConvert(String.class, LocalDate.class)).isTrue();
    }

    @Test
    void canConvertReturnsFalseForUnregisteredTypePair() {
      var reg = SolrConverterRegistration.of(String.class, LocalDate.class, LocalDate::parse);
      var converter = new SolrMappingConverter(new SolrCustomConversions(List.of(reg)));

      assertThat(converter.canConvert(Integer.class, LocalDate.class)).isFalse();
    }

    @Test
    void convertAppliesRegisteredConverter() {
      var reg = SolrConverterRegistration.of(String.class, LocalDate.class, LocalDate::parse);
      var converter = new SolrMappingConverter(new SolrCustomConversions(List.of(reg)));

      var result = converter.convert("2024-06-01", LocalDate.class);

      assertThat(result).isEqualTo(LocalDate.of(2024, 6, 1));
    }

    @Test
    void convertThrowsWhenNoConverterRegistered() {
      var converter = new SolrMappingConverter();

      assertThatThrownBy(() -> converter.convert("2024-06-01", LocalDate.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("No converter registered");
    }

    @Test
    void firstMatchingConverterIsUsedWhenMultipleAreRegistered() {
      var first = SolrConverterRegistration.of(String.class, LocalDate.class,
          s -> LocalDate.of(1111, 1, 1));
      var second = SolrConverterRegistration.of(String.class, LocalDate.class,
          s -> LocalDate.of(2222, 2, 2));
      var converter = new SolrMappingConverter(new SolrCustomConversions(List.of(first, second)));

      var result = converter.convert("anything", LocalDate.class);

      assertThat(result).isEqualTo(LocalDate.of(1111, 1, 1));
    }
  }
}
